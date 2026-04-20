package com.project.vortex.callsagent.presentation.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.project.vortex.callsagent.domain.model.FollowUp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AgendaScreen(
    onFollowUpSelected: (String) -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val agenda by viewModel.agenda.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (agenda.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uiState.isRefreshing) "Loading agenda..." else "No follow-ups scheduled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    agenda.forEach { (section, items) ->
                        item(key = "header-${section.name}") {
                            SectionHeader(section, items.size)
                        }
                        items(items, key = { it.mobileSyncId }) { followUp ->
                            FollowUpRow(
                                followUp = followUp,
                                onClick = { onFollowUpSelected(followUp.clientId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: AgendaSection, count: Int) {
    val label = when (section) {
        AgendaSection.TODAY -> "Today"
        AgendaSection.TOMORROW -> "Tomorrow"
        AgendaSection.THIS_WEEK -> "This week"
        AgendaSection.LATER -> "Later"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FollowUpRow(followUp: FollowUp, onClick: () -> Unit) {
    val timeLabel = remember(followUp.scheduledAt) {
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        formatter.format(followUp.scheduledAt.atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = followUp.clientName ?: "Client",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!followUp.clientPhone.isNullOrBlank()) {
                    Text(
                        text = followUp.clientPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = followUp.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T =
    androidx.compose.runtime.remember(key) { calculation() }
