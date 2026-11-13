package uk.co.ccooper.predictor.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.ccooper.predictor.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app doesn't have the Firebase project baked in. On launch it downloads the
 * project's google-services.json from the league website (and caches it), so the
 * same APK works as soon as the Firebase project is set up.
 */
object Config {
    private const val PREFS = "config"
    private const val KEY_JSON = "google_services_json"

    /** Returns null on success, or a message explaining why setup isn't possible yet. */
    suspend fun init(context: Context): String? {
        if (FirebaseApp.getApps(context).isNotEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_JSON, null)
        val fetched = runCatching { download(BuildConfig.CONFIG_URL) }
        val fresh = fetched.getOrNull()?.takeIf { parse(it, context.packageName) != null }
        if (fresh != null && fresh != cached) prefs.edit().putString(KEY_JSON, fresh).apply()
        // Prefer the copy we started with this session if we had one; a changed file applies next launch.
        val json = cached?.takeIf { parse(it, context.packageName) != null } ?: fresh
            ?: return if (fetched.isFailure)
                "Couldn't reach the league server. Check your internet connection and try again."
            else
                "This league hasn't been set up yet (no Firebase settings found at ${BuildConfig.CONFIG_URL})."
        return apply(context, json)
    }

    /** Manual setup: paste the contents of google-services.json. */
    fun applyPasted(context: Context, json: String): String? {
        if (parse(json, context.packageName) == null) return "That doesn't look like a google-services.json file."
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_JSON, json).apply()
        if (FirebaseApp.getApps(context).isNotEmpty()) return null
        return apply(context, json)
    }

    private fun apply(context: Context, json: String): String? {
        val options = parse(json, context.packageName) ?: return "Invalid Firebase settings."
        return runCatching {
            FirebaseApp.initializeApp(context, options)
            // "demo-" projects only exist in the local Firebase emulators (used for testing).
            if (options.projectId?.startsWith("demo-") == true) {
                com.google.firebase.auth.FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
                com.google.firebase.firestore.FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8085)
            }
            null
        }.getOrElse { "Couldn't start Firebase: ${it.message}" }
    }

    /** Reads a google-services.json, choosing the client entry for our package if there are several. */
    fun parse(text: String, packageName: String): FirebaseOptions? = runCatching {
        val root = JSONObject(text)
        val info = root.getJSONObject("project_info")
        val clients = root.getJSONArray("client")
        val client = (0 until clients.length()).map { clients.getJSONObject(it) }.let { all ->
            all.firstOrNull {
                it.optJSONObject("client_info")?.optJSONObject("android_client_info")
                    ?.optString("package_name") == packageName
            } ?: all.first()
        }
        FirebaseOptions.Builder()
            .setProjectId(info.getString("project_id"))
            .setApplicationId(client.getJSONObject("client_info").getString("mobilesdk_app_id"))
            .setApiKey(client.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
            .setStorageBucket(info.optString("storage_bucket").ifBlank { null })
            .setGcmSenderId(info.optString("project_number").ifBlank { null })
            .build()
    }.getOrNull()

    private suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.useCaches = false
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
