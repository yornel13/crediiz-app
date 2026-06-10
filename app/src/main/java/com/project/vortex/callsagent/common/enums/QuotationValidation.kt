package com.project.vortex.callsagent.common.enums

/**
 * Validation state of a client's quotation (`quotation.validation`).
 * A closed set of 3 values — drives the badge/status in the UI.
 *
 * Mirror of the backend enum. Latest-only: each client has at most one
 * quotation, overwritten on every save.
 */
enum class QuotationValidation {
    /** Submitted, awaiting the bank's decision. */
    PENDING,

    /** Bank approved the quotation. */
    APPROVED,

    /** Bank rejected the quotation. */
    REJECTED,
}
