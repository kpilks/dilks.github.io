package uk.co.ccooper.predictor.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.ccooper.predictor.BuildConfig
import uk.co.ccooper.predictor.R
import uk.co.ccooper.predictor.data.Repo
import uk.co.ccooper.predictor.data.UserProfile

sealed interface Route {
    data class EditWeek(val weekId: String?) : Route
    data class PicksFor(val weekId: String, val uid: String) : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, me: UserProfile) {
    val adminUids by vm.adminUids.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val isAdmin = me.uid in adminUids
    var tab by rememberSaveable { mutableStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (!isAdmin && tab == 2) tab = 0
    val route = stack.lastOrNull()
    BackHandler(enabled = route != null) { stack.removeAt(stack.lastIndex) }
    fun push(r: Route) = stack.add(r)
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    val title = when (route) {
        is Route.EditWeek -> if (route.weekId == null) "New game week" else "Manage game week"
        is Route.PicksFor -> "Edit picks"
        null -> when (tab) { 0 -> "My picks"; 1 -> "League"; else -> "Admin" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (route == null && tab == 0) stringResource(R.string.app_name) else title) },
                navigationIcon = {
                    if (route != null) IconButton(onClick = ::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (route == null && tab != 2) {
                        if (refreshing) Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        else IconButton(onClick = vm::refreshNow) { Icon(Icons.Filled.Refresh, "Refresh scores") }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Column { Text(me.teamName); Text(Repo.auth.currentUser?.email.orEmpty(), style = MaterialTheme.typography.bodySmall) } },
                            onClick = {}, enabled = false,
                        )
                        DropdownMenuItem(text = { Text("Change team name") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Share the app") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menu = false; shareApp(context) })
                        DropdownMenuItem(text = { Text("Sign out") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                            onClick = { menu = false; Repo.signOut() })
                    }
                },
            )
        },
        bottomBar = {
            if (route == null) NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Checklist, null) }, label = { Text("Picks") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Leaderboard, null) }, label = { Text("League") })
                if (isAdmin) NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.AdminPanelSettings, null) }, label = { Text("Admin") })
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (route) {
                is Route.EditWeek -> WeekEditorScreen(vm, route.weekId, onDone = ::pop, onEditPicks = { w, u -> push(Route.PicksFor(w, u)) })
                is Route.PicksFor -> PicksScreen(vm, me, forUid = route.uid, fixedWeekId = route.weekId)
                null -> when (tab) {
                    0 -> PicksScreen(vm, me)
                    1 -> LeagueScreen(vm, me)
                    else -> AdminScreen(vm, onOpenWeek = { push(Route.EditWeek(it)) }, onEditPicks = { w, u -> push(Route.PicksFor(w, u)) })
                }
            }
        }
    }

    if (renaming) RenameDialog(me.teamName, onDismiss = { renaming = false }) { newName ->
        vm.run("Team name updated", onDone = { renaming = false }) { Repo.renameTeam(me.uid, me.teamName, newName) }
    }
}

fun shareApp(context: android.content.Context) {
    val text = "Join our football prediction league! Download the app here: ${BuildConfig.SHARE_URL}"
    context.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share the app"))
}

@Composable
fun RenameDialog(current: String, title: String = "Change team name", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    val error = Repo.validateTeamName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Team name") },
                isError = error != null, supportingText = { error?.let { Text(it) } })
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = error == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
