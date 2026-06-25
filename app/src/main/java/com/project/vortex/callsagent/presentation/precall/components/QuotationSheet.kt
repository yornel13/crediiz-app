package com.project.vortex.callsagent.presentation.precall.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.project.vortex.callsagent.R
import com.project.vortex.callsagent.domain.model.Quotation
import com.project.vortex.callsagent.ui.components.FullHeightBottomSheet
import java.text.NumberFormat
import java.util.Locale

private const val MAX_BANK_LEN = 120
private const val MAX_NOTES_LEN = 1000

/** USD formatting — raw amounts come from the backend without currency. */
internal fun formatUsd(amount: Double): String =
    "$" + NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)

/**
 * Quotation summary card in the client detail. Shows the registered
 * quotation (bank, amounts, notes) or an empty state with a CTA to add one.
 */
@Composable
fun QuotationCard(
    quotation: Quotation?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Asymmetric padding: the trailing edit IconButton carries its own
        // 48dp touch target, so we trim the end/top padding to keep the
        // pencil visually aligned with the 16dp content margin instead of
        // floating far from the edge.
        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.quotation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Single pencil affordance for both create and edit — replaces
                // the old full-width buttons that dominated the card.
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(
                            if (quotation == null) R.string.quotation_action_add
                            else R.string.quotation_action_edit,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (quotation == null) {
                Text(
                    text = stringResource(R.string.quotation_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.padding(top = 4.dp))
                QuotationField(R.string.quotation_field_bank, quotation.bank)
                QuotationField(R.string.quotation_field_amount, formatUsd(quotation.quotedAmount))
                QuotationField(
                    R.string.quotation_field_biweekly,
                    formatUsd(quotation.biweeklyPayment),
                )
                quotation.notes?.takeIf { it.isNotBlank() }?.let {
                    QuotationField(R.string.quotation_field_notes, it)
                }
            }
        }
    }
}

@Composable
private fun QuotationField(labelRes: Int, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Create/edit form for the client's quotation. Full-object upsert — the
 * caller sends the whole object. All fields required except notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotationSheet(
    initial: Quotation?,
    onDismiss: () -> Unit,
    onConfirm: (
        bank: String,
        quotedAmount: Double,
        biweeklyPayment: Double,
        notes: String?,
    ) -> Unit,
) {
    var bank by remember { mutableStateOf(initial?.bank.orEmpty()) }
    var amount by remember {
        mutableStateOf(initial?.quotedAmount?.let { trimAmount(it) } ?: "")
    }
    var biweekly by remember {
        mutableStateOf(initial?.biweeklyPayment?.let { trimAmount(it) } ?: "")
    }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    val amountValue = amount.toDoubleOrNull()
    val biweeklyValue = biweekly.toDoubleOrNull()
    val canSave = bank.isNotBlank() &&
        amountValue != null && amountValue >= 0 &&
        biweeklyValue != null && biweeklyValue >= 0

    FullHeightBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.quotation_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value = bank,
                    onValueChange = { if (it.length <= MAX_BANK_LEN) bank = it },
                    label = { Text(stringResource(R.string.quotation_field_bank)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.quotation_field_amount)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && amountValue == null,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = biweekly,
                    onValueChange = { biweekly = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.quotation_field_biweekly)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = biweekly.isNotEmpty() && biweeklyValue == null,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= MAX_NOTES_LEN) notes = it },
                    label = { Text(stringResource(R.string.quotation_field_notes_optional)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                )

                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = {
                        val a = amount.toDoubleOrNull() ?: return@Button
                        val b = biweekly.toDoubleOrNull() ?: return@Button
                        onConfirm(bank.trim(), a, b, notes.trim().ifBlank { null })
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}

/** Render a stored amount without trailing ".0" so the field reads cleanly. */
private fun trimAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
