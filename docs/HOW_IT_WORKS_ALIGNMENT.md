# Alineación con `HOW_IT_WORKS.md` — Checklist

> **Origen:** `calls-core/docs/HOW_IT_WORKS.md` (2026-05). El backend ya
> implementa el modelo nuevo; la app móvil está desfasada. Este doc es la
> lista accionable, ordenada de **mayor a menor complejidad**, para cerrar
> el gap.
>
> Doc hermano (más narrativo, con código kotlin de referencia):
> [`STATUS_MODEL_REFACTOR_2026-05.md`](STATUS_MODEL_REFACTOR_2026-05.md).
>
> **Modo desarrollo:** la pérdida de datos locales NO es bloqueante. Migrar
> con `fallbackToDestructiveMigration()` y reseed del backend.

---

## Tabla resumen — un vistazo

| Tier | Item | Complejidad | Severidad |
|---|---|---|---|
| 1 | T-A · Termómetro `InterestLevel` (COLD/WARM/HOT) end-to-end | 🟥🟥🟥🟥 alta | P1 |
| 2 | T-B · Renombrar `CallOutcome` al nuevo enum | 🟥🟥🟥🟥 alta | **P0** |
| 3 | T-C · `IN_PROGRESS` + `UNREACHABLE` + remover `INVALID_NUMBER` | 🟥🟥🟥 media-alta | **P0** |
| 4 | T-D · Cambio de status sin llamar (agent-status-change) | 🟥🟥🟥 media-alta | P1 |
| 5 | T-E · Botones **Opt-Out** y **Sold** en PostCall | 🟥🟥 media | P1 |
| 6 | T-F · Robustez: outbox cleanup, refetch post-sync, tests | 🟥🟥 media | P0 (acompaña T-B) |
| 7 | T-G · Badge `wrongNumberCount` en card del cliente | 🟥 baja | P2 |
| 8 | T-H · Mapeo `CallOutcome → ClientStatus` en repo: delegar al backend | 🟥 baja | **P0** |
| 9 | T-I · Strings y colores legacy (`INVALID_NUMBER` → `UNREACHABLE`) | 🟥 baja | P1 |

**Bloqueantes (P0):** T-B, T-C, T-F, T-H. Todo lo demás es mejora funcional o cosmética sin la cual la app sí funciona, aunque sin reflejar el modelo nuevo.

---

## T-A · Termómetro `InterestLevel` end-to-end 🟥🟥🟥🟥

**Por qué es el más complejo:** feature nuevo completo. Toca dominio, persistencia, red, UI y reglas de invalidación.

### Backend ya disponible
- Enum `InterestLevel { COLD, WARM, HOT }` (default `COLD` al entrar a INTERESTED).
- Campo `client.interestLevel: InterestLevel | null`.
- `PATCH /clients/:id/interest-level` (rol AGENT, body `{ level }`).
- Reset automático a `null` cuando `status !== INTERESTED`.

### Trabajo en mobile
- [ ] Añadir enum `InterestLevel` en `common/enums/`.
- [ ] Añadir campo `interestLevel: InterestLevel?` en `domain/model/Client.kt`.
- [ ] Añadir columna `interestLevel` en `ClientEntity` + bump de DB version (Room destructive migration acepta).
- [ ] Extender `ClientDto`, `ClientMapper` para parsear/serializar.
- [ ] Nuevo método en `ClientsApi`: `PATCH /clients/:id/interest-level`.
- [ ] Repository: `updateInterestLevel(clientId, level)` que escribe optimista en Room y dispara la llamada HTTP; revierte ante error.
- [ ] UI:
  - Chips/segmented control de 3 estados (COLD/WARM/HOT) visible solo cuando `status === INTERESTED`. Ubicación natural: PreCall hero o card de detalle.
  - Color codificado (azul/ámbar/rojo).
- [ ] Al cambiar a un outcome que saca al cliente de INTERESTED → reset local del `interestLevel` a `null` (espejo del comportamiento del backend para que no haya flicker antes del re-fetch).

---

## T-B · Renombrar `CallOutcome` 🟥🟥🟥🟥 — **BLOQUEANTE**

**Severidad P0:** el backend valida con `@IsEnum(CallOutcome)` estricto. Cada interaction sincronizada hoy con el enum legacy recibe **HTTP 400** y queda atrapada en la outbox del WorkManager con reintentos infinitos.

### Mapeo
| Legacy (mobile) | Nuevo (backend) |
|---|---|
| `INTERESTED` | `ANSWERED_INTERESTED` |
| `NOT_INTERESTED` | `ANSWERED_NOT_INTERESTED` |
| `NO_ANSWER` | `NO_ANSWER` (sin cambio) |
| `BUSY` | `BUSY` (sin cambio) |
| `INVALID_NUMBER` | `WRONG_NUMBER` |
| — | `ANSWERED_OPT_OUT` (nuevo — ver T-E) |
| — | `ANSWERED_SOLD` (nuevo — ver T-E) |

### Trabajo en mobile
- [ ] Reescribir `common/enums/CallOutcome.kt` con los 7 valores nuevos.
- [ ] Auditar TODOS los `when (outcome)` exhaustivos del proyecto. Lista conocida:
  - `data/repository/ClientRepositoryImpl.kt` (mapa OUTCOME_TO_STATUS — ver T-H).
  - `domain/call/CallEndingInsight.kt`.
  - `presentation/autocall/AutoCallSession.kt` (counters por outcome).
  - `presentation/autocall/AutoCallOrchestrator.kt` (`shouldAutoAdvanceFor`).
  - `presentation/precall/PreCallScreen.kt` (badge labels en historial).
  - `presentation/postcall/PostCallScreen.kt` (botones).
  - `ui/theme/StatusColors.kt` (colors y labels).
- [ ] `data/local/entity/InteractionEntity.kt` — la columna `outcome` guarda el string del enum. Sin convertir, los registros locales viejos quedan inválidos al hidratar; aceptable en modo dev con DB wipe.
- [ ] `data/remote/dto/SyncDto.kt` — verificar que serializa el name del enum tal cual (Moshi default).
- [ ] Tests de mapeo enum ↔ DTO si existieren.

---

## T-C · `IN_PROGRESS` + `UNREACHABLE` + remover `INVALID_NUMBER` 🟥🟥🟥 — **BLOQUEANTE**

**Severidad P0:** el backend mueve cualquier cliente PENDING a IN_PROGRESS al primer outcome `NO_ANSWER`, `BUSY` o `WRONG_NUMBER`. Si el mobile no conoce ese valor, el parser de Moshi explota o cae al default. **Prácticamente todos los clientes activos terminarán siendo "desconocidos" para la app.**

### Trabajo en mobile
- [ ] `common/enums/ClientStatus.kt`: añadir `IN_PROGRESS`, `UNREACHABLE`. Eliminar `INVALID_NUMBER`.
- [ ] Bump de DB version (destructive OK).
- [ ] Auditar todas las queries del `ClientDao`:
  - `observePendingNeverCalled()` → ahora **literalmente** `WHERE status = 'PENDING'`.
  - `observePendingForRetry()` → ya no se basa en `callAttempts > 0` con status PENDING; ahora `WHERE status = 'IN_PROGRESS'`. **Cambio de semántica clave** — el backend asigna IN_PROGRESS automáticamente, así que el cliente puede aparecer en Retry sin que el agente haya hecho la llamada localmente (caso: otro agente lo tuvo asignado antes).
  - Cualquier query que filtre por `INVALID_NUMBER` → cambiar a `UNREACHABLE`.
- [ ] `data/repository/ClientRepositoryImpl.kt`: revisar `observeRecent`, `searchRecent`, hidratación parcial por status.
- [ ] UI:
  - StatusColors: nueva entrada `IN_PROGRESS` (gris/azul) y `UNREACHABLE` (rojo oscuro).
  - Labels en español: "En progreso", "Inalcanzable".
- [ ] Verificar `LoginViewModel.hydrate()` — añadir `refreshAssigned(IN_PROGRESS)` para que la sección Retry se llene en el primer login.

---

## T-D · Cambio de status sin llamar (agent-status-change) 🟥🟥🟥

**Por qué es alta:** feature nuevo de UX, con backend listo. La complejidad está en el modelo de UI (sheet con dropdown de status + reason opcional + interestLevel cuando aplica) y en no chocar con el flujo de dismissal existente.

### Backend ya disponible
- `POST /clients/:id/agent-status-change` (rol AGENT).
- Body: `{ toStatus: ClientStatus, reason?: string, interestLevel?: InterestLevel }`.
- Restricciones: solo si el cliente está asignado al agente solicitante. Validación de transiciones permitidas en `ClientsService`.

### Trabajo en mobile
- [ ] Nuevo método en `ClientsApi`.
- [ ] Repository: `agentStatusChange(clientId, toStatus, reason?, interestLevel?)`.
- [ ] Nueva `AgentStatusChangeSheet` en `presentation/common/` (o `presentation/precall/components/`). Bottom sheet con:
  - Dropdown de status destino (filtrado por transiciones permitidas).
  - TextField de motivo (opcional, max ~200 chars).
  - Si `toStatus === INTERESTED`: chips de InterestLevel.
- [ ] Entry point UI: botón "⚙️ Cambiar status (sin llamar)" en Client Detail (PreCall). **No** mezclarlo con el botón de Dismiss existente: este permite destino arbitrario y queda en el audit trail; el dismiss siempre va a `DISMISSED`.

---

## T-E · Botones Opt-Out y Sold en PostCall 🟥🟥

### Trabajo
- [ ] Añadir entradas `ANSWERED_OPT_OUT` y `ANSWERED_SOLD` al enum (ver T-B).
- [ ] PostCall UI:
  - Layout actual: 5 botones. Pasar a 7 (4 ANSWERED + 3 NO_ANSWER/BUSY/WRONG_NUMBER) con grid responsive 2×4 o 3+3+1.
  - Iconografía consistente con el doc: 😊 / 😐 / 🚫 / 💰 / 📵 / 📞 / ❓.
- [ ] `CallEndingInsight`: cuando la heurística detecta llamada respondida, los `allowedOutcomes` ahora incluyen los 4 ANSWERED_*. Cuando detecta fallo de marcado, solo `WRONG_NUMBER`.
- [ ] `AutoCallSession`: añadir counters `optOut`, `sold` y mapearlos en el resumen de sesión.
- [ ] `AutoCallOrchestrator.shouldAutoAdvanceFor`: hoy retorna `true` para todo. Confirmar política para `ANSWERED_SOLD` (terminal — la lógica del doc sugiere que la auto-call PARE en una venta cerrada).

---

## T-F · Robustez: outbox cleanup, refetch post-sync, tests 🟥🟥 — **acompaña T-B**

Sin esta tarea, T-B deja contaminación en Room: las interactions ya guardadas con outcome legacy intentan sincronizar y el server las rechaza para siempre.

### Trabajo
- [ ] **Wipe puntual de la outbox** al primer arranque con la nueva DB version: aprovechar la destructive migration (DB wipe completo) → no hace falta cleanup explícito.
- [ ] Post-sync: confirmar que `refreshAssigned()` se llama para los nuevos statuses (`IN_PROGRESS`) además de los que ya cubre.
- [ ] Tests:
  - Mapeo enum `CallOutcome` ↔ DTO string (round-trip).
  - Mapeo enum `ClientStatus` ↔ DTO string (round-trip).
  - `OUTCOME_TO_STATUS` map actualizado o eliminado (ver T-H).

---

## T-G · Badge `wrongNumberCount` 🟥

**Backend** ya expone el campo. Solo es UI.

### Trabajo
- [ ] Añadir `wrongNumberCount: Int` al `Client` mobile + entity + mapper + DTO.
- [ ] Mostrar badge "Wrong number ×N" en la card del cliente cuando `wrongNumberCount > 0` y `status === IN_PROGRESS`.
- [ ] Limpiar badge cuando el status sale de IN_PROGRESS (espejo del backend).

---

## T-H · `OUTCOME_TO_STATUS` map: delegar al backend 🟥 — **BLOQUEANTE**

**Severidad P0:** el mapa local en `ClientRepositoryImpl.kt` (líneas ~17-25) hoy decide el status local en función del outcome. Con el modelo nuevo:
- `NO_ANSWER`, `BUSY` → ya NO mantienen el status (van a IN_PROGRESS si era PENDING).
- `WRONG_NUMBER` → puede ir a IN_PROGRESS o a UNREACHABLE según `wrongNumberCount`. El mobile **no debe** tomar esa decisión: solo el backend conoce el contador histórico.

### Trabajo
- [ ] Eliminar el mapa `OUTCOME_TO_STATUS` local. La app solo emite el outcome via sync; el status lo escribe el backend y la app lo recibe por re-fetch.
- [ ] Mantener una actualización **optimista mínima** en Room (e.g. limpiar el cliente de Pendientes-Untouched al primer outcome) si la UX lo requiere, pero esa actualización es presentacional, no semántica.
- [ ] Después del sync exitoso, forzar `refreshAssigned()` para tomar el estado fuente de verdad.

---

## T-I · Strings y colores legacy 🟥

Una vez T-B y T-C estén hechos, el compilador encontrará la mayoría de estos:

- [ ] `ui/theme/StatusColors.kt`: añadir `IN_PROGRESS`, `UNREACHABLE`; eliminar `INVALID_NUMBER`.
- [ ] Labels en español del MANUAL_USUARIO.md (sección §4 outcomes).
- [ ] Cualquier preview de Compose que muestre el chip viejo.

---

## Orden recomendado de pull

1. **T-B + T-C + T-H + T-F** en un mismo cambio → cierra los 4 bloqueantes P0. La app vuelve a sincronizar contra el backend deployed.
2. **T-I** — sale gratis con la pasada del compilador.
3. **T-E** — botones Opt-Out y Sold. Cubre los outcomes nuevos en la UI.
4. **T-A** — termómetro completo.
5. **T-D** — agent-status-change sheet.
6. **T-G** — badge wrongNumberCount (cosmético).

---

## Definition of done global

- [ ] `./gradlew :app:assembleDebug` limpio.
- [ ] Login + sync end-to-end contra el backend deployado sin 400s en logcat.
- [ ] Cada outcome (los 7) producible desde PostCall; el cliente termina en el status correcto según `HOW_IT_WORKS.md`.
- [ ] Termómetro se actualiza desde mobile y persiste tras reabrir la app.
- [ ] Agent puede cambiar a `DO_NOT_CALL` sin llamar (caso WhatsApp).
- [ ] Audit trail en backend muestra todos los movimientos correctamente atribuidos al agente.
- [ ] Doc `STATUS_MODEL_REFACTOR_2026-05.md` archivado o marcado como completado.
- [ ] `OVERVIEW.md`, `ARCHITECTURE.md`, `MANUAL_USUARIO.md` actualizados.
