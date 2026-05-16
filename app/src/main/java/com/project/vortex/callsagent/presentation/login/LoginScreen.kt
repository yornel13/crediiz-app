package com.project.vortex.callsagent.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.project.vortex.callsagent.presentation.common.WindowSize
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Wire-level identifiers for the `reason` query arg. Mirrors
 * `SessionInvalidationReason` but kept as plain strings here to avoid
 * dragging a domain enum into Nav arg parsing.
 */
object LoginReason {
    const val INVALIDATED = "INVALIDATED"
    const val EXPIRED = "EXPIRED"
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    reason: String? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        launch {
            viewModel.events.collect { event ->
                when (event) {
                    LoginEvent.Success -> onLoginSuccess()
                }
            }
        }
        // Forward blocking auth errors (AccountDisabled, Network, etc.)
        // to the snackbar host. Recoverable errors (wrong credentials)
        // are shown inline via state.errorMessage instead.
        launch {
            viewModel.snackbarMessages.collect { msg ->
                snackbarHostState.showSnackbar(
                    message = msg.text,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    // Adaptive form width: compact stretches edge-to-edge, medium/expanded
    // cap the form so inputs stay readable instead of becoming 1300px-wide
    // tap targets on a tablet in landscape.
    val formMaxWidth = when {
        WindowSize.isExpandedWidth -> 560.dp
        WindowSize.isMediumWidth -> 480.dp
        else -> Dp.Unspecified
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Outer Column: occupies the full viewport, becomes scrollable when
        // the IME shrinks the available height (tablet landscape with the
        // soft keyboard open would otherwise hide the password field, with
        // no way to reach it). imePadding() pushes content above the keyboard.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // consumeWindowInsets prevents double-padding: the Scaffold
                // already accounted for system bars via `padding`. Without
                // this, imePadding() would stack the keyboard inset ON TOP
                // of the navigation-bar inset and leave a visible white
                // strip between the form and the soft keyboard.
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Inner form container: single source of truth for the form
            // width on large screens. Keeps inputs readable on tablets
            // without each field re-declaring its own widthIn.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = formMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Calls Agent",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sign in to start working with your queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                ReasonBanner(reason = reason)

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.errorMessage?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            // size() locks both width AND height; height-only
                            // leaves the default 40.dp width and renders an
                            // ellipse taller than wide ratio looks busted.
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = "Sign in",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Two-stage progress hint — first auth, then data hydration.
                if (state.isBusy) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (state.isHydrating) "Loading your queue..." else "Signing in...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Banner shown above the credentials when the user landed on /login
 * because the previous session ended unexpectedly. The copy splits the
 * single-active-session ("another device took over") case from the
 * generic 401 ("token expired") case so the agent understands what
 * happened.
 */
@Composable
private fun ReasonBanner(reason: String?) {
    if (reason.isNullOrBlank()) return

    val message = when (reason) {
        LoginReason.INVALIDATED ->
            "Tu sesión se cerró desde otro dispositivo o por el administrador. " +
                "Vuelve a iniciar sesión."
        LoginReason.EXPIRED ->
            "Tu sesión expiró. Por favor inicia sesión nuevamente."
        else -> return
    }

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
