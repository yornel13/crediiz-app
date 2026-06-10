package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.QuotationValidation
import java.time.Instant

/**
 * A client's quotation — a structured note the agent fills after evaluating
 * the client with a bank. **Latest-only**: embedded in the client and
 * overwritten on every save (no quotation history).
 *
 * [updatedBy] / [updatedAt] are set by the backend on save; the app reads
 * them but never sends them.
 *
 * [quotedAmount] / [biweeklyPayment] are raw numbers — currency formatting
 * is the UI's responsibility.
 */
data class Quotation(
    val validation: QuotationValidation,
    val bank: String,
    val quotedAmount: Double,
    val biweeklyPayment: Double,
    val notes: String?,
    val updatedBy: String?,
    val updatedAt: Instant?,
)
