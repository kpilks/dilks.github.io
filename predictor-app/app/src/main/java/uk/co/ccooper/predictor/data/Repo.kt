package uk.co.ccooper.predictor.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale

/**
 * Firestore layout:
 *   meta/admins                 { uids: [..] }            first user to register becomes admin
 *   teamNames/{lowercase name}  { uid, name }             keeps team names unique
 *   users/{uid}                 { teamName, name, weekPoints: {weekId: pts}, createdAt }
 *   weeks/{weekId}              { title, deadline, status, fixtures: [..], createdAt }
 *   weeks/{weekId}/picks/{uid}  { uid, picks: {fixtureId: H|D|A}, banker, updatedAt }
 */
object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val admins get() = db.collection("meta").document("admins")
    private fun user(uid: String) = db.collection("users").document(uid)
    private fun teamDoc(team: String) = db.collection("teamNames").document(teamKey(team))
    private fun week(id: String) = db.collection("weeks").document(id)
    private fun picks(weekId: String, uid: String) = week(weekId).collection("picks").document(uid)

    // ---------- team names ----------

    fun cleanTeamName(raw: String) = raw.trim().replace(Regex("\\s+"), " ")
    fun teamKey(team: String) = cleanTeamName(team).lowercase(Locale.ROOT)

    /** Null if OK, otherwise a message to show. */
    fun validateTeamName(raw: String): String? {
        val t = cleanTeamName(raw)
        return when {
            t.length < 2 -> "Team name must be at least 2 characters"
            t.length > 30 -> "Team name must be 30 characters or fewer"
            t.none { it.isLetterOrDigit() } -> "Team name needs at least one letter or number"
            t.any { !(it.isLetterOrDigit() || it in " '&.-!_()") } -> "Only letters, numbers, spaces and ' & . - ! _ ( ) please"
            else -> null
        }
    }

    // ---------- live listeners ----------

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    private fun <T> docFlow(ref: DocumentReference, map: (Map<String, Any?>?) -> T): Flow<T> = callbackFlow {
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) { trySend(map(null)); return@addSnapshotListener }
            trySend(map(snap?.data))
        }
        awaitClose { reg.remove() }
    }

    private fun <T> queryFlow(q: Query, onError: (Exception) -> Unit = {}, map: (String, Map<String, Any?>) -> T): Flow<List<T>> =
        callbackFlow {
            val reg = q.addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                trySend(snap?.documents?.map { map(it.id, it.data ?: emptyMap()) } ?: emptyList())
            }
            awaitClose { reg.remove() }
        }

    fun profileFlow(uid: String): Flow<UserProfile?> = docFlow(user(uid)) { m -> m?.let { UserProfile.from(uid, it) } }

    fun adminUidsFlow(): Flow<Set<String>> = docFlow(admins) { m ->
        (m?.get("uids") as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
    }

    fun usersFlow(): Flow<List<UserProfile>> = queryFlow(db.collection("users")) { id, m -> UserProfile.from(id, m) }

    fun weeksFlow(): Flow<List<Week>> =
        queryFlow(db.collection("weeks").orderBy("deadline", Query.Direction.DESCENDING)) { id, m -> Week.from(id, m) }

    /** Everyone's picks for a week. Only allowed after the deadline (or for admins). */
    fun allPicksFlow(weekId: String, onError: (Exception) -> Unit): Flow<List<Picks>> =
        queryFlow(week(weekId).collection("picks"), onError) { id, m -> Picks.from(id, m) }

    fun picksFlow(weekId: String, uid: String): Flow<Picks?> =
        docFlow(picks(weekId, uid)) { m -> m?.let { Picks.from(uid, it) } }

    // ---------- accounts ----------

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String, name: String, team: String) {
        // Check the team name first so people don't end up with an account but no team.
        if (teamDoc(team).get().await().exists()) throw IllegalStateException("That team name is taken")
        val uid = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!.uid
        createProfile(uid, name, team)
    }

    suspend fun createProfile(uid: String, name: String, team: String) {
        val clean = cleanTeamName(team)
        if (teamDoc(clean).get().await().exists()) throw IllegalStateException("That team name is taken")
        db.batch()
            .set(teamDoc(clean), mapOf("uid" to uid, "name" to clean))
            .set(user(uid), mapOf(
                "teamName" to clean,
                "name" to name.trim(),
                "weekPoints" to emptyMap<String, Any>(),
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            .commit().await()
        claimAdminIfFirst(uid)
    }

    /** The very first person to create a team becomes the admin. */
    private suspend fun claimAdminIfFirst(uid: String) {
        runCatching {
            if (!admins.get().await().exists()) admins.set(mapOf("uids" to listOf(uid))).await()
        }
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /** Rename a team (the player themself, or an admin). */
    suspend fun renameTeam(uid: String, oldName: String, newName: String) {
        val clean = cleanTeamName(newName)
        if (teamKey(clean) == teamKey(oldName)) {
            user(uid).update("teamName", clean).await()
            return
        }
        if (teamDoc(clean).get().await().exists()) throw IllegalStateException("That team name is taken")
        db.batch()
            .set(teamDoc(clean), mapOf("uid" to uid, "name" to clean))
            .update(user(uid), "teamName", clean)
            .delete(teamDoc(oldName))
            .commit().await()
    }

    // ---------- picks ----------

    suspend fun savePicks(weekId: String, uid: String, picks: Map<String, String>, banker: String?) {
        picks(weekId, uid).set(mapOf(
            "uid" to uid,
            "picks" to picks,
            "banker" to banker,
            "updatedAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    // ---------- admin ----------

    suspend fun saveWeek(id: String?, title: String, deadline: Long, status: String, fixtures: List<Fixture>): String {
        val ref = if (id == null) db.collection("weeks").document() else week(id)
        val data = mutableMapOf<String, Any?>(
            "title" to title.trim(),
            "deadline" to Timestamp(Date(deadline)),
            "status" to status,
            "fixtures" to fixtures.map { it.toMap() },
        )
        if (id == null) data["createdAt"] = FieldValue.serverTimestamp()
        ref.set(data, SetOptions.merge()).await()
        return ref.id
    }

    suspend fun setWeekStatus(id: String, status: String) {
        week(id).update("status", status).await()
    }

    suspend fun setFixtures(id: String, fixtures: List<Fixture>) {
        week(id).update("fixtures", fixtures.map { it.toMap() }).await()
    }

    suspend fun deleteWeek(id: String) {
        val all = week(id).collection("picks").get().await().documents
        all.chunked(400).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { b.delete(it.reference) }
            b.commit().await()
        }
        week(id).delete().await()
    }

    /**
     * Works out everyone's points for the week using [fixtures] (live data already
     * merged in) and writes them to each player's record, then marks the week finalised.
     * Safe to run again after correcting a result.
     */
    suspend fun finalise(week: Week, fixtures: List<Fixture>, users: List<UserProfile>) {
        val allPicks = week(week.id).collection("picks").get().await().documents
            .associate { it.id to Picks.from(it.id, it.data ?: emptyMap()) }
        val writes = users.map { u -> u.uid to Scoring.score(allPicks[u.uid], fixtures).points }
        writes.chunked(400).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { (uid, pts) -> b.update(user(uid), FieldPath.of("weekPoints", week.id), pts) }
            b.commit().await()
        }
        db.batch()
            .update(week(week.id), mapOf("fixtures" to fixtures.map { it.toMap() }, "status" to WeekStatus.FINALISED))
            .commit().await()
    }

    /** Admin: add a player who doesn't have the app (e.g. on iPhone). Returns their id. */
    suspend fun addManualPlayer(name: String, team: String): String {
        val clean = cleanTeamName(team)
        if (teamDoc(clean).get().await().exists()) throw IllegalStateException("That team name is taken")
        val uid = "manual-" + db.collection("users").document().id
        db.batch()
            .set(teamDoc(clean), mapOf("uid" to uid, "name" to clean))
            .set(user(uid), mapOf(
                "teamName" to clean,
                "name" to name.trim(),
                "manual" to true,
                "weekPoints" to emptyMap<String, Any>(),
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            .commit().await()
        return uid
    }

    suspend fun setAdmin(uid: String, admin: Boolean) {
        admins.update("uids", if (admin) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)).await()
    }

    suspend fun removePlayer(p: UserProfile) {
        db.batch().delete(user(p.uid)).delete(teamDoc(p.teamName)).commit().await()
    }
}
