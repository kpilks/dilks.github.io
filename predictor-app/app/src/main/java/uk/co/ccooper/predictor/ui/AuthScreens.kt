package uk.co.ccooper.predictor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.ccooper.predictor.R
import uk.co.ccooper.predictor.data.Repo
import androidx.compose.ui.res.stringResource

@Composable
fun AppRoot(vm: AppViewModel) {
    val boot by vm.boot.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.showMessage(null)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (val b = boot) {
                Boot.Loading -> Centered { CircularProgressIndicator() }
                is Boot.Failed -> SetupScreen(b.message, onRetry = vm::start, onPaste = vm::applyPastedConfig)
                Boot.Ready -> when {
                    user == null -> AuthScreen(vm)
                    profile == null -> Centered { CircularProgressIndicator() }
                    profile?.value == null -> CreateTeamScreen(vm)
                    else -> HomeScreen(vm, profile!!.value!!)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 72.dp))
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun Brand(subtitle: String) {
    Spacer(Modifier.height(24.dp))
    Icon(Icons.Filled.SportsSoccer, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SetupScreen(message: String, onRetry: () -> Unit, onPaste: (String) -> Unit) {
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var json by rememberSaveable { mutableStateOf("") }
    FormColumn {
        Brand("Pick 10 results each week. Bank one for a bonus point.")
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        TextButton(onClick = { showPaste = !showPaste }) { Text("Organiser: enter settings manually") }
        if (showPaste) {
            OutlinedTextField(
                json, { json = it }, Modifier.fillMaxWidth().height(200.dp),
                label = { Text("Paste google-services.json") },
            )
            OutlinedButton(onClick = { onPaste(json) }, enabled = json.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Use these settings")
            }
        }
    }
}

@Composable
private fun AuthScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun go(block: suspend () -> Unit) {
        busy = true
        vm.task(block) { busy = false }
    }

    FormColumn {
        Brand("Pick 10 results each week. Bank one for a bonus point.")
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Sign in") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Register") })
        }
        if (tab == 1) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Your name") },
                supportingText = { Text("So the organiser knows who you are") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            val teamError = team.takeIf { it.isNotBlank() }?.let(Repo::validateTeamName)
            OutlinedTextField(team, { team = it }, Modifier.fillMaxWidth(), label = { Text("Team name") },
                supportingText = { Text(teamError ?: "This is what shows in the league table") },
                isError = teamError != null, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        }
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") },
            singleLine = true,
            supportingText = if (tab == 1) ({ Text("At least 6 characters") }) else null,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))

        if (tab == 0) {
            Button(
                onClick = { go { Repo.signIn(email, password) } },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Sign in") }
            TextButton(onClick = {
                if (email.isBlank()) vm.showMessage("Enter your email first")
                else go { Repo.resetPassword(email); vm.showMessage("Password reset email sent to ${email.trim()}") }
            }) { Text("Forgotten password?") }
        } else {
            val ok = name.isNotBlank() && Repo.validateTeamName(team) == null && email.isNotBlank() && password.length >= 6
            Button(
                onClick = { go { Repo.register(email, password, name, team) } },
                enabled = !busy && ok,
                modifier = Modifier.fillMaxWidth(),
            ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Create account") }
        }
    }
}

/** Shown if someone is signed in but has no team (e.g. sign-up was interrupted, or they were removed). */
@Composable
private fun CreateTeamScreen(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val uid = vm.user.collectAsStateWithLifecycle().value?.uid ?: return
    FormColumn {
        Brand("Nearly there — choose your team name.")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Your name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        val teamError = team.takeIf { it.isNotBlank() }?.let(Repo::validateTeamName)
        OutlinedTextField(team, { team = it }, Modifier.fillMaxWidth(), label = { Text("Team name") },
            supportingText = { Text(teamError ?: "This is what shows in the league table") },
            isError = teamError != null, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        Button(
            onClick = {
                busy = true
                vm.task({ Repo.createProfile(uid, name, team) }) { busy = false }
            },
            enabled = !busy && name.isNotBlank() && Repo.validateTeamName(team) == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Join the league") }
        TextButton(onClick = { Repo.signOut() }) { Text("Sign out") }
    }
}
