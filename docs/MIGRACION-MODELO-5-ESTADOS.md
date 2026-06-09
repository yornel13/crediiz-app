# Migración al modelo de 5 estados del cliente

> **Estado:** ✅ implementada y compilando (`compileDebugKotlin` verde, KSP/Room/Hilt incluidos).
> Pendiente: verificación funcional en dispositivo. · **Ámbito:** app de agentes (`calls-agends`)
> **Fuentes de verdad (backend `calls-core`):**
> - `calls-core/FRONTEND-ESTADOS.md` — contrato de estados y endpoints.
> - `calls-core/docs/flujo-de-estados-cliente.md` — guía maestra de comportamiento.
>
> Este documento traduce ese contrato a un plan de cambios concreto para la app. Los nombres
> de negocio van en español; los nombres internos (`PENDING`, `CallOutcome`, etc.) son los del código.

---

## 1. Por qué cambiamos (contexto y motivación)

El backend rediseñó el ciclo de vida del cliente. Pasó de **8 estados + un sub-nivel de termómetro
(frío/tibio/caliente)** a **5 estados con motivo de baja**. El cambio no es solo de catálogo: cambia
*quién decide el estado*.

**Antes (modelo actual de la app):** la app calculaba el estado localmente a partir del resultado de
la llamada (mapa `OPTIMISTIC_OUTCOME_TO_STATUS`) y lo aplicaba de inmediato.

**Ahora:** el estado es **global, único por cliente y lo deriva el backend** del historial completo de
interacciones, aplicando reglas de precedencia por fecha de evento. **La app ya no calcula estados:**
registra resultados de llamada, dispara acciones y **muestra lo que el backend resuelve**.

La consecuencia práctica es que el estado de un cliente puede **cambiar solo** tras sincronizar, y eso
**es correcto** (consistencia eventual). La app debe convivir con eso, no pelearlo.

---

## 2. Qué se necesita y para qué (alcance)

### 2.1 Dentro de alcance (lo que toca la app)

Solo **3 endpoints** del agente tocan estados:

| Endpoint | Uso |
|---|---|
| `GET /clients/assigned?status=` | Traer mi agenda (el backend filtra por el agente del token). |
| `POST /sync/interactions` | Registrar llamadas; el backend mueve el estado según el `outcome`. |
| `POST /clients/:id/agent-status-change` | Cambiar estado sin llamada (incluye baja a `REMOVED`). |

Más el rediseño de **4 enums** y la **migración de la base local (Room)**.

### 2.2 Fuera de alcance

- **Todo el panel administrativo:** filtro por `category`, `PATCH /clients/:id/status`, `reactivate`,
  `assign`/`unassign`, `dashboard`, `status-history`, `interested`, `GET /clients/:id`. **No los consume la app.**
- **No se toca:** sistema de follow-ups/agenda, autenticación, envelope de respuesta, base URL ni el
  formato de errores RFC-9457. Ya funcionan y el contrato no los cambia.

---

## 3. Reglas de comportamiento que la app DEBE respetar

Extraídas de la guía maestra (`flujo-de-estados-cliente.md`). No son opcionales: son la lógica nueva.

1. **Estado global y único.** Un solo `status` + `removalReason` por cliente, igual para todos los agentes.
2. **El front no calcula estados.** El backend los deriva del historial. La app registra y muestra.
3. **High-water mark — el agente solo sube.** Escala `PENDING < INTERESTED < CITED < CONVERTED`. Un
   agente nunca baja de nivel. Intentar bajar = el backend ignora.
4. **No-op silencioso (200).** Si una transición está bloqueada, `agent-status-change` responde **200
   con el cliente sin cambios**. La app **debe comparar el `status` devuelto**, no asumir que cambió.
5. **Quórum de 2 agentes para motivos "duros"** (no llamar / equivocado / préstamo / fallecido). Una
   sola marca **no remueve** al cliente; hace falta un 2º agente distinto. Que siga activo tras una
   marca **es correcto**. La app **no remueve localmente** por estos motivos.
6. **`CONVERTED` lo bloquea la app.** Al firmarse, el cliente deja de aparecer en la agenda; el bloqueo
   es responsabilidad del front. Solo el admin saca de `CONVERTED`.
7. **Revivir desde `REMOVED`.** Un agente puede revivir un cliente removido a `INTERESTED`/`CITED` con un
   avance posterior. `REMOVED` no es una pared absoluta para la app.
8. **Consistencia eventual = convergencia silenciosa.** El estado puede mostrar un valor que luego
   cambie tras sincronizar. **Es esperado.** Sin toasts de error ni rollbacks ruidosos.

---

## 4. Tablas de traducción canónicas (núcleo del riesgo de datos)

Estas tablas gobiernan tanto la migración de Room como el mapeo en UI. Cualquier dato local con
valores viejos se traduce con ellas.

### 4.1 `ClientStatus` (viejo → nuevo)

| Viejo (8) | Nuevo | Nota |
|---|---|---|
| `PENDING` | `PENDING` | — |
| `IN_PROGRESS` | `PENDING` | "Llamado sin desenlace" ahora es Pendiente; se deriva de `callAttempts`/`lastOutcome`. |
| `INTERESTED` | `INTERESTED` | — |
| `CONVERTED` | `CONVERTED` | — |
| `REJECTED` | `REMOVED` (`NOT_INTERESTED`) | — |
| `UNREACHABLE` | `REMOVED` (`UNREACHABLE`) | — |
| `DO_NOT_CALL` | `REMOVED` (`DO_NOT_CALL`) | — |
| `DISMISSED` | `REMOVED` (según `reasonCode`, o `OTHER`) | Ver 4.3. |
| — | `CITED` | **Nuevo.** Sin equivalente viejo; llega vía outcome `SCHEDULED`. |

### 4.2 `CallOutcome` (viejo → nuevo)

| Viejo (7) | Nuevo |
|---|---|
| `ANSWERED_INTERESTED` | `INTERESTED` |
| `ANSWERED_NOT_INTERESTED` | `NOT_INTERESTED` |
| `ANSWERED_OPT_OUT` | `DO_NOT_CALL` |
| `ANSWERED_SOLD` | `SOLD` |
| `NO_ANSWER` | `NO_ANSWER` |
| `BUSY` | `BUSY` |
| `WRONG_NUMBER` | `WRONG_NUMBER` |
| — | **Nuevos:** `SCHEDULED`, `OUT_OF_SERVICE`, `VOICEMAIL`, `HAS_LOAN`, `DECEASED`, `NOT_APPLICABLE` |

### 4.3 `DismissalReasonCode` (mobile-only viejo) → `RemovalReason` (canónico)

> El backend nunca conoció estos códigos; el mapeo lo decide la app. **Decisión por defecto** (ver §8).

| Viejo | Nuevo |
|---|---|
| `OPTOUT` | `DO_NOT_CALL` |
| `OTHER` | `OTHER` |
| `CORPORATE_NUMBER` | `NOT_APPLICABLE` |
| `INVALID_DATA` | `NOT_APPLICABLE` |
| `DUPLICATE` | `NOT_APPLICABLE` |
| `OUT_OF_SCOPE` | `NOT_APPLICABLE` |

### 4.4 Efecto real de cada `CallOutcome` (de §10 de la guía) y mapa optimista permitido

| `CallOutcome` | Efecto en el estado (lo hace el backend) | ¿La app aplica estado local? |
|---|---|---|
| `INTERESTED` | → `INTERESTED` | ✅ avance directo seguro |
| `SCHEDULED` | → `CITED` | ✅ avance directo seguro |
| `SOLD` | → `CONVERTED` | ✅ avance directo seguro |
| `NO_ANSWER` / `BUSY` / `OUT_OF_SERVICE` / `VOICEMAIL` | sigue Pendiente; 5 sin contacto → `REMOVED (UNREACHABLE)` | ❌ no toca estado |
| `NOT_INTERESTED` | sigue Pendiente; 5 → `REMOVED (NOT_INTERESTED)` | ❌ no toca estado |
| `DO_NOT_CALL` / `WRONG_NUMBER` / `HAS_LOAN` / `DECEASED` | `REMOVED` **con quórum de 2 agentes** | ❌ **nunca** local |
| `NOT_APPLICABLE` | `REMOVED (NOT_APPLICABLE)` (manual) | depende del canal |

**Mapa optimista resultante (de 7 entradas a 3):** solo `INTERESTED→INTERESTED`, `SCHEDULED→CITED`,
`SOLD→CONVERTED`. Son avances high-water-mark, seguros. El resto: registrar el outcome, sincronizar y
esperar la verdad del backend.

---

## 5. Cómo lo resolvemos — plan por fases

Orden de base a UI; cada fase deja el árbol compilando.

### Fase 1 — Enums (capa base)
`common/enums/`
- `ClientStatus.kt` → 5 valores; agregar `CITED`, eliminar `IN_PROGRESS`/`REJECTED`/`UNREACHABLE`/`DO_NOT_CALL`/`DISMISSED`.
- `CallOutcome.kt` → 13 valores; renombrar `ANSWERED_*` y agregar los 6 nuevos.
- **Nuevo** `RemovalReason.kt` (8 valores), **enum simple** sin `labelRes` (las etiquetas se resuelven en la capa UI, mejor SoC).
- **Eliminar** `InterestLevel.kt` y `DismissalReasonCode.kt`.

### Fase 2 — Dominio + DTOs
- `domain/model/Client.kt`: +`removalReason`; −`interestLevel`, −`assignedTo`/`assignedAt` (N:M), −`wrongNumberCount`.
- `data/remote/dto/ClientDto.kt`: idem en `ClientResponse` (campos internos del backend se ignoran).
- `AgentStatusChangeDto`: quitar `interestLevel`, agregar `removalReason`.
- Revisar `Interaction` / `ClientDismissal` por referencias a enums eliminados.

### Fase 3 — Room: esquema (sin migración de datos — DECIDIDO)
**Decisión:** estamos en desarrollo → la base local es un caché desechable; **no se escribe `MIGRATION`**.
- `AppDatabase` versión `9 → 10`; `DatabaseModule` con `fallbackToDestructiveMigration(dropAllTables = true)`
  (API vigente de Room 2.7; nada deprecado). Al subir la versión, Room **borra y recrea** la base.
  ⚠️ Comentado en `DatabaseModule`: **revisar antes del cutover a producción** (un release no debe destruir cola sin sincronizar en campo).
- `ClientEntity`: +`removalReason`, −`interestLevel`, −`wrongNumberCount`.
- `LocalAgentStatusChangeEntity`: `interestLevel` → `removalReason`.
- `Converters`: −`InterestLevel`, +`RemovalReason`.
- `ClientDao`: feeds recodificados (never-called = `PENDING AND callAttempts=0`; retry = `PENDING AND callAttempts>0`);
  `applyInteractionUpdate`/`refineOutcome` ya **no escriben `status`**; +`setRemovalReason`, −`setInterestLevel`.

### Fase 4 — Red
- `ClientsApi.kt`: eliminar `PATCH /clients/{id}/interest-level`; ajustar payload de `agentStatusChange`; revisar `status` de `getAssigned` (`IN_PROGRESS` ya no es válido).
- Mappers (`ClientMapper`, `InteractionMapper`, `ClientDismissalMapper`): alinear enums/campos.

### Fase 5 — Repositorio / lógica (cambio de fondo)
`data/repository/ClientRepositoryImpl.kt`
- `OPTIMISTIC_OUTCOME_TO_STATUS` → `SAFE_ADVANCE_OUTCOME_TO_STATUS` (3 entradas seguras).
- **Reconciliación post-200** en `agentStatusChange`: **sin optimista**; se escribe el cliente **devuelto** por el
  backend y se retorna el `ClientStatus` resultante (`OperationResult<ClientStatus, ClientError>`) para detectar el no-op.
- **Subsistema de descarte ELIMINADO por completo** (DECIDIDO): borrados `ClientDismissal{Dao,Entity,Mapper,RepositoryImpl}`,
  modelo e interfaz de dominio, `SyncDismissalDto` + campos `dismissals` del sync, y su procesamiento en `SyncManager`.
  El descarte del agente ahora va por `agent-status-change → REMOVED + removalReason` (consciente del quórum). Sin `undo`.
- `SyncManager`: el pull refresca también `CITED` (estado activo nuevo).

### Fase 6 — UI + UX de consistencia eventual
- Pantallas con enums viejos: outcome sheet, post-call, dismiss sheet (→ sheet de `removalReason`), agenda, clientes, recientes.
- Modelar `CITED` en la agenda y como destino de `SCHEDULED`.
- Convergencia silenciosa: el estado puede cambiar tras refresh sin tratarlo como error.
- Quórum: feedback "registrado, pendiente de confirmación" sin sacar al cliente de la agenda.
- Ocultar/bloquear `CONVERTED` de la agenda.
- Reagrupar el feed "Pendientes" sin depender de `assignedAt` (usar `createdAt`/`queueOrder`).

### Fase 7 — i18n
`values/` (inglés) y `values-es/` (español): nuevos strings (5 estados + 8 motivos + 13 outcomes),
claves `snake_case` con prefijo de feature. Borrar strings muertos de `InterestLevel`/`DismissalReasonCode`.

---

## 6. Qué cambiaremos — inventario por archivo

| Archivo | Cambio |
|---|---|
| `common/enums/ClientStatus.kt` | Reescribir a 5 valores. |
| `common/enums/CallOutcome.kt` | Reescribir a 13 valores. |
| `common/enums/RemovalReason.kt` | **Crear.** |
| `common/enums/InterestLevel.kt` | **Eliminar.** |
| `common/enums/DismissalReasonCode.kt` | **Eliminar.** |
| `domain/model/Client.kt` | +`removalReason`; −`interestLevel`/`assignedTo`/`assignedAt`/`wrongNumberCount`. |
| `data/local/entity/ClientEntity.kt` | Idem + cambio de esquema. |
| `data/local/entity/InteractionEntity.kt` | Revisar tipo de `outcome`. |
| `data/local/db/ClientDao.kt` | Feeds recodificados; `applyInteractionUpdate`/`refineOutcome` sin `status`; +`setRemovalReason`. |
| `data/local/db/{Converters,DatabaseModule,AppDatabase}` | `RemovalReason` converter; `fallbackToDestructiveMigration`; versión 10. |
| `data/remote/dto/ClientDto.kt` | `ClientResponse`: +`removalReason`, −`interestLevel`/`wrongNumberCount`; `AgentStatusChangeDto` −`interestLevel` +`removalReason`; −`UpdateInterestLevelDto`. |
| `data/remote/api/ClientsApi.kt` | Eliminar `interest-level`; ajustar `agentStatusChange`/`getAssigned`. |
| `data/mapper/ClientMapper.kt` | Alinear enums/campos. |
| `data/repository/ClientRepositoryImpl.kt` | Mapa optimista reducido + reconciliación post-200. |
| **Subsistema dismissal** (`ClientDismissal{Dao,Entity,Mapper,RepositoryImpl}`, modelo+interfaz, `SyncDismissalDto`) | **Eliminado.** Descarte vía `agent-status-change`. |
| `common/error/{ErrorCodes,ErrorMapper}` | Eliminado `CLIENT_INTEREST_LEVEL_NOT_APPLICABLE`. |
| UI — `AgentStatusChangeSheet`, `DismissClientSheet`, `PreCall`, `PostCall`, `Clients`, `Agenda`, Recientes, resumen autocall, `StatusColors` | Enums nuevos; `CITED`; no-op 200; quórum; termómetro eliminado. |
| `values/` y `values-es/` | Strings de 5 estados + 8 motivos + 13 outcomes + no-op/quórum; borrados los muertos. |

---

## 7. Verificación y riesgos

### Verificación
- ✅ **`compileDebugKotlin` verde** (KSP/Room/Hilt) — sin referencias a enums eliminados.
- ⏳ **Pendiente: smoke en Tab A9+** (per reglas de pantalla): agenda, registrar cada outcome, marcar `REMOVED`
  con motivo, intentar bajar nivel (verificar no-op), `CITED` entra a la agenda, `CONVERTED` oculto, cliente que
  converge tras refresh.

### Riesgos / notas
1. **Wipe destructivo de Room** activo solo para desarrollo. **Revisar antes del cutover a producción** — un
   release no debe borrar la cola sin sincronizar en campo (volver a una `MIGRATION` real o un drenado previo).
2. **`assignedAt`**: si el backend deja de mandarlo (modelo N:M), reanclar el agrupado del feed a `createdAt`/`queueOrder`.

---

## 8. Decisiones tomadas

| # | Decisión | Resolución |
|---|---|---|
| 1 | Migración de datos local | **Sin migración** — wipe destructivo (desarrollo). |
| 2 | Canal de baja del agente | **`agent-status-change → REMOVED + removalReason`**; subsistema dismissal + `undo` eliminados. |
| 3 | Etiquetas de `RemovalReason` | Resueltas en UI (enum simple, no `labelRes`). |
| 4 | Métricas del resumen de auto-llamado | Agrupadas por funnel: interesado / citado / firmado / sin contacto / removido / saltados. |
