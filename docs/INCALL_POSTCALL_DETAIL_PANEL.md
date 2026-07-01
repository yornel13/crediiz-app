# Enriquecimiento del panel de detalle en InCall y PostCall

**Estado:** Plan aprobado para implementar
**Fecha:** 2026-06-29
**Autor:** Tech Lead (análisis asistido)
**Objetivo:** Que el panel derecho de `InCallScreen` y `PostCallScreen` muestre el mismo detalle del cliente que `PreCallScreen` (notas, historial de llamadas, estado, cotización), eliminando la duplicación de código que hoy lo mantiene desactualizado.

---

## 1. Contexto y objetivo

Hoy, al pulsar **Llamar**, el flujo de pantallas es:

```
PreCall (detalle full) → InCall (Activity) → PostCall → Home
```

`PreCallScreen` muestra un detalle rico del cliente. `InCallScreen` y `PostCallScreen`
ya intentan mostrar ese detalle en su panel derecho, pero con una versión recortada
que **omite cotización y banner de estado** y deja el historial filtrado.

El objetivo es que durante la llamada (`InCall`) y al clasificarla (`PostCall`) el
agente vea el mismo contexto que tenía antes de llamar, **sin** los controles de
acción que no aplican (llamar, pausar, skip, cancelar, cambiar estado).

---

## 2. Estado actual (hallazgos de la investigación)

### 2.1 El panel derecho ya es compartido — pero es una reimplementación recortada

| Pantalla | Panel izquierdo | Panel derecho |
|---|---|---|
| `InCallScreen` | `CallControlPanel` (mute, ruta de audio, colgar, nota en vivo) | `PreCallReadOnlyPanel` |
| `PostCallScreen` | `PostCallLeftPanel` (outcome, follow-up, nota, Guardar) | `PreCallReadOnlyPanel` |

`PreCallReadOnlyPanel` (`PreCallScreen.kt:476-562`) **no reutiliza** `PreCallContent`
(`PreCallScreen.kt:565-696`). Es una segunda `LazyColumn` escrita a mano que renderiza
solo un subconjunto. Esa es la causa raíz.

### 2.2 Qué renderiza cada lista hoy

| Sección | `PreCallContent` (full) | `PreCallReadOnlyPanel` (panel derecho) |
|---|:---:|:---:|
| `CompactHeader` (identidad, datos, status pill) | ✅ | ✅ (pill no-op) |
| `CallReadinessBanner` (SIP) | ✅ | ❌ |
| `ContextualBanner` (convertido / removido / callback / lead nuevo) | ✅ | ❌ **falta** |
| `QuotationCard` (cotización) | ✅ | ❌ **falta** |
| Activity header + contador | ✅ | ✅ |
| `QuickNoteInline` (escribir nota) | ✅ | ❌ |
| Timeline de actividad | ✅ | ⚠️ mismo filtro (`showFullActivityHistory`) |

### 2.3 No hay déficit de datos, solo de render

`PreCallReadOnlyPanel` monta su **propio** `PreCallViewModel` con key `"readonly-$clientId"`
(`PreCallScreen.kt:480-483`). Ese ViewModel **ya carga todo** lo necesario:

- `activity` → timeline completo (llamadas previas, notas, cambios de estado, follow-ups).
- `client.quotation` → cotización.
- `client.status` + `nextFollowUp` → estado y banner contextual.

Conclusión: **lo que se pide ya está en memoria; solo no se dibuja.**

### 2.4 La edición de cotización es viable sin tocar los ViewModels de la llamada

El mismo `PreCallViewModel` del panel derecho expone `saveQuotation(...)`
(`PreCallViewModel.kt:241-284`), que llama a `clientRepository.upsertQuotation(...)`
(`ClientRepositoryImpl.kt:208-238`). La persistencia escribe en Room y **todos los
Flows** que observan ese `clientId` se re-emiten (`ClientDao.observeById`), así que:

- El panel izquierdo de `PostCall` (que observa el mismo cliente vía su `PostCallUiState.client`) **se actualiza solo**.
- No hay que inyectar `ClientRepository` en `PostCallViewModel` ni en `InCallViewModel`.

> **Side-effect a tener presente:** `saveQuotation` promueve automáticamente al cliente
> de `PENDING` a `INTERESTED` antes de guardar (`PreCallViewModel.kt:249-259`). Ver §5.3.

### 2.5 Tamaño de archivos (regla de 1000 líneas)

| Archivo | Líneas | Estado |
|---|---:|---|
| `PreCallScreen.kt` | 2485 | ❌ viola el cap de 1000 |
| `PostCallScreen.kt` | 1030 | ❌ sobre el cap |
| `InCallScreen.kt` | 941 | ⚠️ cerca |

El trabajo obliga a tocar `PreCallScreen.kt`; es la oportunidad para extraer el
contenido compartido a `components/` y bajar de las 1000 líneas (§4, Fase 3).

---

## 3. Decisiones tomadas

| # | Decisión | Implicación |
|---|---|---|
| 1 | El timeline **respeta** el setting `showFullActivityHistory`. | No se fuerza vista completa; se mantiene el comportamiento actual. |
| 2 | La cotización es **editable** desde el panel derecho. | El panel deja de ser estrictamente read-only: monta `QuotationSheet` y llama a `saveQuotation`. |
| 3 | El status pill es **solo display**. El cambio de estado se hace en el panel izquierdo. | Pill no-op (como hoy). Ver discrepancia en §3.1. |

### 3.1 ⚠️ Discrepancia detectada en la decisión 3

Afirmaste que el cambio de estado "ya se hace en el panel izquierdo". La verificación
muestra un matiz importante:

- **Ni `InCallScreen` ni `PostCallScreen` tienen un control de `ClientStatus`** en su
  panel izquierdo. No hay `AgentStatusChangeSheet` en esas pantallas.
- `PostCallLeftPanel` ofrece un selector de **`CallOutcome`** (INTERESADO, CITADO,
  CONVERTIDO, …), que **no es** lo mismo que `ClientStatus`. El `ClientStatus` se deriva
  del outcome al guardar, no se elige directamente.
- El control manual de estado (`AgentStatusChangeSheet`) vive **solo** en `PreCallScreen`.

**Qué significa:** dejar el pill como display en el panel derecho es correcto, pero el
"cambio de estado en el panel izquierdo" en realidad ocurre **de forma indirecta** vía el
outcome (en PostCall) y no existe como tal durante InCall. Si esperabas un selector de
estado explícito en el panel izquierdo, hoy no existe y queda fuera de este scope.

**Acción requerida:** confirmar que el comportamiento indirecto (outcome → estado) es
suficiente, o abrir un ticket aparte para un control de estado explícito. Este documento
asume que **sí es suficiente** y no añade control de estado.

### 3.2 Decisiones derivadas (no preguntadas, fijadas por consistencia)

| Elemento en panel derecho | Decisión | Razón |
|---|---|---|
| `QuickNoteInline` (escribir nota) | **Oculto** | La nota se captura en el panel izquierdo (nota en vivo en InCall, nota post-call en PostCall). Dos campos de nota competiendo confunde. "Ver notas" = timeline, no escribir. |
| `CallReadinessBanner` (SIP) | **Oculto** | La preparación de SIP ya ocurrió antes de llamar; no aplica durante/después. |
| Botón calendario del header (agendar follow-up) | **Oculto** | Es una acción de escritura no solicitada. En PostCall el follow-up se captura en el outcome form. Fuera de scope. |
| `QuotationCard` | **Visible y editable** | Decisión 2. |
| `ContextualBanner` | **Visible** | Es solo informativo (estado del cliente). |
| Status pill | **Display (no-op)** | Decisión 3. |

---

## 4. Diseño de la solución

### 4.1 Principio: una sola fuente de verdad para el contenido

En lugar de seguir parchando `PreCallReadOnlyPanel` para que se parezca a
`PreCallContent`, se **unifican** en un único composable parametrizado por callbacks
nullable. El patrón ya existe en el codebase: `CompactHeader` recibe `onBack`,
`onStatusClick`, `onScheduleClick` como nullable y oculta/desactiva según corresponda.

**Regla:** un callback `null` ⇒ la acción asociada no se renderiza (o queda no-op).
Esto respeta ISP (cada modo pasa solo lo que necesita) y elimina la duplicación (DRY).

### 4.2 Composable unificado

```kotlin
// Nuevo: presentation/precall/components/ClientDetailContent.kt
@Composable
fun ClientDetailContent(
    client: Client,
    activity: List<ActivityEvent>,
    nextFollowUp: FollowUp?,
    showFullActivityHistory: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    // --- Acciones opcionales: null = no se muestra / no-op ---
    callReadiness: CallReadiness? = null,        // null = sin banner SIP
    isSubmittingNote: Boolean = false,
    onRetrySip: (() -> Unit)? = null,
    onSaveNote: ((String) -> Unit)? = null,      // null = sin QuickNoteInline
    onBack: (() -> Unit)? = null,
    onStatusClick: (() -> Unit)? = null,         // null = pill display-only
    onScheduleClick: (() -> Unit)? = null,       // null = sin botón calendario
    onRequestQuotation: (() -> Unit)? = null,    // null = QuotationCard no editable
)
```

### 4.3 Cómo lo consume cada llamador

| Parámetro | `PreCallContent` (full) | `PreCallReadOnlyPanel` (panel derecho) |
|---|---|---|
| `callReadiness` | estado real | `null` |
| `onSaveNote` | `viewModel::saveManualNote` | `null` |
| `onBack` | nav back | `null` |
| `onStatusClick` | abre `AgentStatusChangeSheet` | `null` (display) |
| `onScheduleClick` | abre `ScheduleFollowUpSheet` | `null` |
| `onRequestQuotation` | abre `QuotationSheet` | **abre `QuotationSheet` local** |

El resultado: el panel derecho pasa a mostrar `ContextualBanner` + `QuotationCard`
(editable) además del header y el timeline, con el mismo filtro de actividad.

### 4.4 Edición de cotización en el panel derecho

`PreCallReadOnlyPanel` debe ganar estado de sheet local y cablear el guardado a su
propio ViewModel (que ya tiene el método):

```kotlin
var showQuotationSheet by remember { mutableStateOf(false) }

ClientDetailContent(
    /* ... */
    onRequestQuotation = { showQuotationSheet = true },
)

if (showQuotationSheet) {
    QuotationSheet(
        initial = uiState.client?.quotation,
        onDismiss = { showQuotationSheet = false },
        onConfirm = { bank, amount, biweekly, notes ->
            viewModel.saveQuotation(bank, amount, biweekly, notes)
            showQuotationSheet = false
        },
    )
}
```

No se toca `PostCallViewModel` ni `InCallViewModel`. La propagación a los demás paneles
es automática vía Room (§2.4).

---

## 5. Edge cases y riesgos

### 5.1 InCall: `currentClient` se vuelve `null` al desconectar
`InCallScreen` cachea `stableClient` (`InCallScreen.kt:113-117`) para que el panel no
parpadee durante el hold post-disconnect (1.2 s). Hay que verificar que el `clientId`
pasado a `PreCallReadOnlyPanel` provenga de ese cache estable, y que abrir el
`QuotationSheet` justo cuando la llamada termina no deje el sheet huérfano. **Mitigación:**
cerrar el sheet si `uiState.client == null`.

### 5.2 Doble `PreCallViewModel` activo durante la llamada
El panel derecho instancia un `PreCallViewModel` independiente del ViewModel de la
pantalla. Ya es así hoy; enriquecer el render no añade costo de datos (el VM ya los
trae). Solo confirmar que la key `"readonly-$clientId"` evita colisión de estado con la
instancia full cuando se navega Pre → In → Post del mismo cliente.

### 5.3 `saveQuotation` promueve `PENDING → INTERESTED` — VERIFICADO, sin colisión

Editar la cotización desde el panel derecho promueve al cliente de `PENDING` a
`INTERESTED`. **Decisión del producto: se mantiene tal cual** (cotizar implica interés).
Verificación de colisiones realizada — veredicto por punto:

| Punto verificado | Veredicto | Detalle |
|---|---|---|
| PostCall reacciona a `client.status` | **NO CHOCA** | `canSave`/`showFollowUpForm`/`allowedOutcomes` dependen del `selectedOutcome`, no del `client.status` (`PostCallViewModel.kt:72-86`). |
| Doble `StatusChanged` en timeline | **NO CHOCA** | `agentStatusChange` solo registra si `entity.status != fromStatus` (`ClientRepositoryImpl.kt:178-190`); el outcome aplica el estado vía `dao.setStatus` local sin segundo evento de backend. |
| AutoCallOrchestrator / cola | **NO CHOCA** | La cola es un snapshot fijo por `clientIds`; promover a `INTERESTED` no recalcula el "siguiente". |
| InCall reacciona a status | **NO CHOCA** | InCall lee un snapshot del cliente; ignora cambios de status en vivo. |
| Idempotencia de `agentStatusChange` | **OK** | Si `toStatus == status` actual, el backend responde no-op y no se registra nada. |

**Único matiz no bloqueante:** cotizar (`→INTERESTED`) y luego marcar `NOT_INTERESTED`
en PostCall intenta un downgrade local que el backend rechaza por el modelo
high-water-mark. Es el comportamiento **ya existente** del flujo y se preserva sin
cambios. No requiere acción en este scope.

### 5.4 Reglas de pantalla (memoria `ui_screen_rules`)
Se tocan layouts adaptativos (split vs tabs). Verificar: window insets explícitos,
sin lock de orientación, empty-state defensivo, y prueba en **Tab A9+ (SM-X216B)**.

### 5.5 Consistencia visual entre modos
Al unificar, el panel derecho hereda paddings/encabezados de `PreCallContent`. Revisar
que el `contentPadding` (que en full venía del `Scaffold`) se generalice bien cuando no
hay `Scaffold` (panel derecho).

---

## 6. Plan de implementación por fases

### Fase 1 — Unificar el contenido (núcleo del pedido) ✅ HECHO
- [x] Generalizar `PreCallContent` con los callbacks nullable de §4.2 (sigue en `PreCallScreen.kt`; `null` oculta la pieza).
- [x] `PreCallReadOnlyPanel` **delega** en `PreCallContent` en vez de su `LazyColumn` propia, pasando: `callReadiness=null`, `onRetrySip=null`, `onSaveNote=null`, `onBack=null`, `onRequestSchedule=null`, `onRequestStatusChange={}`, `onRequestQuotation={...}`.
- [x] El panel derecho ahora muestra `ContextualBanner` + `QuotationCard`, con el timeline respetando el setting `showFullActivityHistory`.

### Fase 2 — Edición de cotización en el panel derecho ✅ HECHO
- [x] Estado `showQuotationSheet` + montaje de `QuotationSheet` en `PreCallReadOnlyPanel` (§4.4).
- [x] `onConfirm` → `viewModel.saveQuotation(...)`.
- [x] Cierre defensivo del sheet si `uiState.client == null` (§5.1).

### Fase 3 — Extracción a archivos (cumplir cap de 1000 líneas) ✅ HECHO
- [x] `PreCallScreen.kt`: **2488 → 709 líneas**. `compileDebugKotlin` en verde.
- [x] Archivos nuevos en `presentation/precall/`:
  - `PreCallHeader.kt` (404) — `CompactHeader`, data grid, status pill, paletas.
  - `PreCallContextualBanner.kt` (163) — banner contextual y helpers.
  - `PreCallActivityTimeline.kt` (637) — rail, gutter, rows, dispatch, formatters.
  - `PreCallActionComponents.kt` (679) — `CallActionBar`, `QuickNoteInline`, etc.
- [x] Visibilidades `private → internal` solo donde hay uso cross-file (`CompactHeader`, `ContextualBanner`, `computeBannerContext`, `BannerContext`, `renderActivityTimeline`, `ActivityEmptyState`, `formatTimestamp`, `formatSalary`, `CallActionBar`, `QuickNoteInline`).

### Fase 4 — QA en dispositivo (PENDIENTE)
- [ ] Tab A9+ split: InCall y PostCall muestran cotización editable + banner + timeline.
- [ ] Teléfono (modo Tabs): pestaña "Cliente" en InCall idem.
- [ ] Editar cotización en panel derecho → confirmar que el panel izquierdo de PostCall refleja el cambio (Room).
- [ ] Verificar promoción `PENDING → INTERESTED` al cotizar (§5.3) y que el timeline registra el cambio.
- [ ] Transición de colgado (hold 1.2 s): el panel no se vacía ni el sheet queda huérfano.

---

## 7. Criterios de aceptación

1. El panel derecho de InCall y PostCall muestra: header con datos, banner contextual de estado, cotización (editable), e historial de actividad (notas + llamadas previas, según setting).
2. **No** aparecen en el panel derecho: botón llamar, pausar, skip, cancelar, cambiar estado, ni campo de nota.
3. La cotización se edita desde el panel derecho y el cambio se refleja en el resto de paneles sin recargar.
4. `PreCallScreen.kt` y `PostCallScreen.kt` quedan bajo 1000 líneas.
5. Existe una sola implementación del contenido de detalle (sin la `LazyColumn` duplicada).
6. Verificado en Tab A9+ y en teléfono.

---

## 8. Archivos afectados (referencia)

| Archivo | Cambio |
|---|---|
| `presentation/precall/PreCallScreen.kt` | Generalizar `PreCallContent`; `PreCallReadOnlyPanel` delega + monta `QuotationSheet`. |
| `presentation/precall/components/ClientDetailContent.kt` | **Nuevo** — contenido unificado (Fase 3). |
| `presentation/precall/components/ActivityTimeline.kt` | **Nuevo** — timeline extraído (Fase 3). |
| `presentation/precall/components/CompactHeader.kt` | **Nuevo** — header extraído (Fase 3). |
| `presentation/incall/InCallScreen.kt` | Asegurar `clientId` desde `stableClient`. Sin cambios de lógica de llamada. |
| `presentation/postcall/PostCallScreen.kt` | Sin cambios (consume `PreCallReadOnlyPanel` ya enriquecido). |
| `presentation/precall/PreCallViewModel.kt` | Sin cambios (ya expone `saveQuotation`). |

> **No se modifican:** `PostCallViewModel`, `InCallViewModel`, `ClientRepository`,
> ni el modelo `Quotation`. La reactividad existente cubre la propagación.
