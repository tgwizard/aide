package com.tgwizard.aide

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tgwizard.aide.verisure.Verisure
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date

/** Verisure setup (login, lock, door code) and a manual test of the door lock. */
@Composable
internal fun VerisureSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(Verisure.account(context)) }
    var awaitingCode by remember { mutableStateOf(false) }
    var locks by remember { mutableStateOf<List<Verisure.Lock>>(emptyList()) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                account = Verisure.account(context)
                busy = false
            }
        }
    }

    fun findLocks() = run { locks = Verisure.discoverLocks(context) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
        SectionHeader("Door · Verisure")
        val current = account
        when {
            awaitingCode -> CodeStep(busy) { code ->
                run {
                    Verisure.verifyMfa(context, code)
                    awaitingCode = false
                    locks = Verisure.discoverLocks(context)
                }
            }
            current == null -> LoginStep(busy) { email, password ->
                run {
                    awaitingCode = Verisure.login(context, email, password)
                    if (!awaitingCode) locks = Verisure.discoverLocks(context)
                }
            }
            current.deviceLabel == null -> {
                if (locks.isEmpty()) {
                    OutlinedButton(onClick = ::findLocks, enabled = !busy) { Text("Find locks") }
                } else {
                    Text("Which lock?")
                    locks.forEach { lock ->
                        OutlinedButton(onClick = { Verisure.selectLock(context, lock); account = Verisure.account(context) }) {
                            Text(lock.name)
                        }
                    }
                }
            }
            current.doorCode == null -> DoorCodeStep { code ->
                Verisure.setDoorCode(context, code)
                account = Verisure.account(context)
            }
            else -> {
                LaunchedEffect(Unit) { status = Verisure.statusJson(context) }
                InfoRow(current.lockName ?: "Lock", status?.let(::describe) ?: "…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { run { status = Verisure.lockJson(context) } },
                        enabled = !busy,
                        border = BorderStroke(1.dp, Bronze),
                    ) { Text("Lock") }
                    OutlinedButton(onClick = { run { status = Verisure.unlockJson(context) } }, enabled = !busy) { Text("Unlock") }
                    TextButton(onClick = { run { status = Verisure.statusJson(context, maxAgeMs = 0) } }, enabled = !busy) {
                        Text("Refresh")
                    }
                }
                TextButton(onClick = {
                    Verisure.logout(context)
                    account = null
                    locks = emptyList()
                    status = null
                }) { Text("Log out of Verisure", color = Muted) }
            }
        }
        if (busy) Text("Working…", color = Muted)
        error?.let { Text(it, color = Ember, maxLines = 4) }
    }
}

@Composable
private fun LoginStep(busy: Boolean, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text("Log in with your Verisure (My Pages) account. Stored encrypted on this phone only.", color = Muted)
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onLogin(email.trim(), password) }, enabled = !busy && email.isNotBlank() && password.isNotEmpty()) {
        Text("Log in")
    }
}

@Composable
private fun CodeStep(busy: Boolean, onVerify: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text("Verisure sent you a verification code (SMS or email).", color = Muted)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onVerify(code) }, enabled = !busy && code.isNotBlank()) { Text("Verify") }
}

@Composable
private fun DoorCodeStep(onSave: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text("Your personal door code, the one you'd type on the lock's keypad. Verisure requires it to lock or unlock.", color = Muted)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit) },
        label = { Text("Door code") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onSave(code) }, enabled = code.length >= 4, colors = ButtonDefaults.buttonColors()) { Text("Save") }
}

private fun describe(status: JSONObject): String {
    status.optString("error").ifEmpty { null }?.let { return "Error: $it" }
    val lock = when (status.optString("lock")) {
        "LOCKED" -> "Locked"
        "UNLOCKED" -> "Unlocked"
        else -> "Unknown"
    }
    val door = if (status.optString("door") == "OPEN") " · door open" else ""
    val at = status.optLong("at", 0).takeIf { it > 0 }?.let { " · ${DateFormat.format("HH:mm", Date(it))}" }.orEmpty()
    return lock + door + at
}
