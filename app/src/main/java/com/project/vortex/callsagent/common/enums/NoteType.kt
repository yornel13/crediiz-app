package com.project.vortex.callsagent.common.enums

/**
 * Categorisation of a `Note`. Mobile only CREATES the first four
 * (`CALL`, `POST_CALL`, `MANUAL`, `FOLLOW_UP`) — `STATUS_CHANGE` and
 * `DISMISSAL` are server-side auto-Notes that mobile may eventually
 * RECEIVE (e.g. when surfacing client history in PreCall). They live
 * in the enum so JSON deserialisation never falls off a cliff when
 * the backend serves them.
 */
enum class NoteType {
    CALL,
    POST_CALL,
    MANUAL,
    FOLLOW_UP,
    STATUS_CHANGE,
    DISMISSAL,
}
