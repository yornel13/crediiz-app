package com.project.vortex.callsagent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.domain.call.CallReadiness

/**
 * Persistent banner pinned above the Home tabs and at the top of
 * Pre-Call. Renders one of three visible states; renders nothing for
 * [CallReadiness.Ready] and [CallReadiness.Unknown].
 *
 *  - [CallReadiness.Unassigned] — admin action needed, red, no retry.
 *  - [CallReadiness.Disconnected] — REGISTER failed, amber, "Reintentar".
 *  - [CallReadiness.Connecting] — REGISTER in flight, gray, transient
 *    (no action button, the orchestrator resolves it).
 */
@Composable
fun CallReadinessBanner(
    state: CallReadiness,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CallReadiness.Ready, CallReadiness.Unknown -> Unit

        CallReadiness.Unassigned -> BannerSurface(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PhoneDisabled,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "No puedes realizar llamadas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "No tienes una cuenta VoIP asignada. " +
                            "Contacta al administrador para que te asigne una.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        is CallReadiness.Disconnected -> BannerSurface(
            // Amber container — distinct from the red Unassigned banner
            // so the agent can tell at a glance which problem it is.
            container = AmberContainer,
            content = AmberOnContainer,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SyncProblem,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Sin conexión con el servidor de llamadas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "No se pudo conectar con Vozelia. " +
                            "Revisa tu red e intenta nuevamente.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRetry) {
                    Text(
                        text = "Reintentar",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        CallReadiness.Connecting -> BannerSurface(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // size() locks BOTH width and height. width()-only would
                // leave the default 40 dp height and the indicator would
                // render as a vertical ellipse (looks like a fan blade).
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Conectando con el servidor de llamadas…",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Espera unos segundos antes de llamar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerSurface(
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    Surface(
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            body()
        }
    }
}

// Material3 doesn't ship a tonal "warning" slot, so we hand-pick a
// pair that reads clearly in both light and dark schemes. Tweak in
// `ui/theme/` if a brand color is ever defined.
private val AmberContainer = Color(0xFFFFE0B2)
private val AmberOnContainer = Color(0xFF5D4037)
