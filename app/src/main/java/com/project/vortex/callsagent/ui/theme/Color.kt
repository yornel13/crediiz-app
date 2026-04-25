package com.project.vortex.callsagent.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Brand palette — "Calls Modern"
// ─────────────────────────────────────────────────────────────────────────────

// Primary — Deep Teal (trust, professional, distinctive vs the usual CRM blue)
val Teal900 = Color(0xFF134E4A)
val Teal700 = Color(0xFF0F766E)
val Teal500 = Color(0xFF14B8A6)
val Teal100 = Color(0xFFCCFBF1)
val Teal50 = Color(0xFFF0FDFA)

// Secondary — Slate (neutral surfaces, text)
val Slate900 = Color(0xFF0F172A)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate300 = Color(0xFFCBD5E1)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

// Tertiary — Coral (rare, for warm highlights)
val Coral500 = Color(0xFFF97066)
val Coral200 = Color(0xFFFECDD3)

// Backgrounds
val OffWhite = Color(0xFFFAFAF9)
val DeepInk = Color(0xFF0B1220)
val DeepInkSurface = Color(0xFF111827)
val DeepInkSurfaceHigh = Color(0xFF1F2937)

// ─────────────────────────────────────────────────────────────────────────────
// Semantic state colors — used for ClientStatus + CallOutcome badges and chips.
// Light/dark variants both readable on their respective surfaces.
// ─────────────────────────────────────────────────────────────────────────────

// Success (INTERESTED)
val Emerald600 = Color(0xFF059669)
val Emerald100 = Color(0xFFD1FAE5)

// Warning (NO_ANSWER, BUSY)
val Amber600 = Color(0xFFD97706)
val Amber100 = Color(0xFFFEF3C7)

// Error (REJECTED, INVALID_NUMBER)
val Rose600 = Color(0xFFE11D48)
val Rose100 = Color(0xFFFFE4E6)

// Info (FOLLOW_UP)
val Sky600 = Color(0xFF0284C7)
val Sky100 = Color(0xFFE0F2FE)

// Neutral (PENDING, MANUAL note)
val Slate600 = Color(0xFF475569)
val Slate200 = Color(0xFFE2E8F0)

// Phone-call accent — the only vibrant green in the app, reserved for the
// primary call action.
val PhoneGreen = Color(0xFF16A34A)
val PhoneGreenDark = Color(0xFF15803D)
