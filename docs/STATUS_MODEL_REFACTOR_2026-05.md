# Refactor del modelo de estados — 2026-05

**Audiencia:** agente que trabaja en `calls-agends` (app móvil Android del agente).
**Propósito:** describir el cambio que el backend (`calls-core`) ya tiene
implementado, y enumerar exactamente qué debe actualizar la app para quedar
alineada.

Este documento es la **fuente única** del cambio para mobile. Después de
implementarlo, actualizar `docs/OVERVIEW.md`, `docs/ARCHITECTURE.md` y
`docs/BACKEND_COORDINATION.md` para reflejar el estado final, y eliminar
este archivo.

---

## 1. Resumen del cambio

| Área | Antes | Después |
|---|---|---|
| `ClientStatus` | 7 valores | **8 valores** (nuevo `IN_PROGRESS`, `INVALID_NUMBER` → `UNREACHABLE`) |
| `CallOutcome` | 6 valores estilo sustantivo | **7 valores estilo verbo** (`ANSWERED_*`, `WRONG_NUMBER`) |
| Sub-eje del cliente | — | **`interestLevel: COLD \| WARM \| HOT`** (solo cuando `status === INTERESTED`) |
| Cuarentena | — | **`wrongNumberCount`** — N `WRONG_NUMBER` consecutivos → `UNREACHABLE` |
| Cambio de status agente | Solo vía outcome de llamada | **`POST /clients/:id/agent-status-change`** (sin llamada) |
| Sub-eje del cliente (UI agente) | — | **`PATCH /clients/:id/interest-level`** para reclasificar a un cliente ya INTERESTED |

**Lo que NO cambia para mobile:**
- El shape de los DTOs `SyncInteractionDto`, `SyncNoteDto`, `SyncFollowUpDto`,
  `SyncDismissalDto` — solo cambian los **valores de enum** que se envían
  dentro de `outcome`.
- El flujo de sync (`POST /sync/*`) — sigue empujando interactions, notas,
  follow-ups y dismissals exactamente igual.
- La forma en que la app crea notas (sigue mandando `agentId` implícito vía
  JWT — el backend hace el snapshot del autor automáticamente).

Razón del refactor: separar **eventos** (`CallOutcome`) de **estados**
(`ClientStatus`), capturar el nivel de interés sin multiplicar estados, y
permitir al agente cambiar el status sin tener que disparar una llamada
falsa.

---

## 2. Enums — definición autoritativa

Mirror exacto de `calls-core/src/common/enums/`. Reemplazar las clases/enums
locales en la capa de dominio de la app.

```kotlin
enum class ClientStatus {
    PENDING,        // nunca llamado
    IN_PROGRESS,    // 🆕 al menos un intento sin desenlace decisivo
    INTERESTED,     // expresó interés (ver interestLevel)
    CONVERTED,      // sale cerrada
    REJECTED,       // declinó explícitamente
    UNREACHABLE,    // 🔄 (era INVALID_NUMBER) terminal tras N WRONG_NUMBER
    DO_NOT_CALL,    // opt-out legal
    DISMISSED,      // descarte del agente (admin puede revertir)
}

enum class CallOutcome {
    ANSWERED_INTERESTED,      // → INTERESTED (default level COLD)
    ANSWERED_NOT_INTERESTED,  // → REJECTED
    ANSWERED_OPT_OUT,         // → DO_NOT_CALL
    ANSWERED_SOLD,            // → CONVERTED (terminal)
    NO_ANSWER,                // → IN_PROGRESS (si era PENDING)
    BUSY,                     // → IN_PROGRESS (si era PENDING)
    WRONG_NUMBER,             // 🔄 (era INVALID_NUMBER) incrementa wrongNumberCount
}

enum class InterestLevel { COLD, WARM, HOT }
```

**Mapeo outcome → status (decidido en backend, NO replicar lógica en mobile):**

| Outcome enviado | Status resultante en backend |
|---|---|
| `ANSWERED_INTERESTED` | `INTERESTED` con `interestLevel = COLD` por default |
| `ANSWERED_NOT_INTERESTED` | `REJECTED` |
| `ANSWERED_OPT_OUT` | `DO_NOT_CALL` |
| `ANSWERED_SOLD` | `CONVERTED` (terminal — la cola del agente lo deja de mostrar) |
| `NO_ANSWER` / `BUSY` | `IN_PROGRESS` si era `PENDING`; sin cambio en otros casos |
| `WRONG_NUMBER` | `IN_PROGRESS` o `UNREACHABLE` si llega al threshold (default 3 consecutivos) |

> Importante: la app **no necesita conocer ese mapeo**. Cuando el cliente se
> resincroniza desde el backend, ya viene con el `status` correcto.

---

## 3. Cambios en el modelo `Client` que recibe la app

```kotlin
data class Client(
    // ... campos existentes ...
    val status: ClientStatus,
    val interestLevel: InterestLevel?,   // 🆕 no-null solo si status == INTERESTED
    val wrongNumberCount: Int,           // 🆕 contador interno, útil para mostrar warning
    // ... resto sin cambios ...
)
```

**Cómo usarlos en UI:**

- Si `status == INTERESTED` → mostrar pill del status + sub-pill del
  `interestLevel` (`Frío` / `Tibio` / `Caliente`).
- Si `wrongNumberCount > 0` → mostrar warning discreto ("N intentos con
  número equivocado"). Cuando el backend marca el cliente como
  `UNREACHABLE`, el agente lo verá fuera de su cola activa (queda
  archivado).

---

## 4. Cambios en el flujo de fin-de-llamada

El DTO `SyncInteractionDto` **no añade campos nuevos**. La única diferencia
es que el valor del campo `outcome` ahora debe ser uno de los 7 nuevos
verb-form values.

**Mapeo de la pantalla de outcome (sugerido):**

| Botón en UI | Valor que se envía |
|---|---|
| "Interesado" | `ANSWERED_INTERESTED` |
| "No interesado" | `ANSWERED_NOT_INTERESTED` |
| "Pidió no llamar / Opt-out" | `ANSWERED_OPT_OUT` |
| "Vendido" | `ANSWERED_SOLD` |
| "No contestó" | `NO_ANSWER` |
| "Ocupado" | `BUSY` |
| "Número equivocado / no es la persona" | `WRONG_NUMBER` |

> El `interestLevel` **no viaja** en el DTO de sync. Cuando el agente marca
> `ANSWERED_INTERESTED`, el cliente queda en `INTERESTED + COLD` por
> default. Para reclasificar a `WARM` o `HOT`, ver §6.

---

## 5. Nuevo: cambio de status sin llamada

Caso de uso: el agente recibe info por otro canal (WhatsApp, mensaje del
cliente, decisión interna) y quiere cambiar el status sin tener que abrir
una llamada falsa.

**Endpoint:** `POST /clients/:id/agent-status-change` (rol AGENT)

**Body:**
```json
{
  "toStatus": "DO_NOT_CALL",        // cualquier ClientStatus válido
  "reason": "Cliente pidió por WhatsApp no recibir más llamadas",
  "interestLevel": "WARM"            // opcional, solo si toStatus === INTERESTED
}
```

**Validación del backend:**
- `reason` es **requerido** cuando `toStatus` es `CONVERTED` o `DO_NOT_CALL`
  (auditoría de transiciones terminales).
- `interestLevel` solo se acepta cuando `toStatus === INTERESTED`. Si se
  omite con ese status, el backend pone `COLD` por default.
- El backend genera automáticamente:
  - Una entrada en `ClientStatusChange` con `source: AGENT_OUT_OF_BAND`,
    snapshot del agente del JWT, y el `reason` recibido.
  - Una `Note` de tipo `STATUS_CHANGE` con el `reason` como contenido (si
    se mandó `reason`), anclada al cambio.

**Respuesta:** el `Client` actualizado.

**Sugerencia UX:** acción "Cambiar status" dentro del detalle del cliente,
con un selector de status, un campo de razón opcional/requerido (según el
target), y un selector adicional de nivel cuando el target es `INTERESTED`.

---

## 6. Nuevo: cambiar nivel de interés a un cliente ya INTERESTED

Caso de uso: el cliente ya está en `INTERESTED` (level `COLD`) y en una
llamada posterior pasa a recibir cotización formal — el agente quiere
subirlo a `WARM` o `HOT` sin volver a marcar `ANSWERED_INTERESTED`.

**Endpoint:** `PATCH /clients/:id/interest-level` (rol AGENT)

**Body:**
```json
{ "level": "HOT" }
```

**Validación:** falla con 400 si el cliente no está en `INTERESTED`.

**Respuesta:** el `Client` actualizado.

**Sugerencia UX:** dentro del detalle de un cliente `INTERESTED`, mostrar
los 3 niveles como un segmented control rápido. Tap directo cambia el
nivel sin diálogo intermedio.

---

## 7. Notas — qué tiene que saber la app

El refactor de notas es **principalmente server-side**. La app sigue
creando notas con su `agentId` actual y enviándolas vía
`POST /sync/notes` exactamente como hoy.

Lo único nuevo a tener en cuenta:

- Existe un nuevo `NoteType` server-side llamado `STATUS_CHANGE`. La app
  **no debe** crear notas con ese tipo — solo el backend las genera al
  registrar transiciones de status. Si la app las recibe (por ejemplo en
  un endpoint de "ver historial del cliente"), debe poder renderizarlas.
- Cuando la app reciba una `Note` desde el backend, puede llegar con autor
  AGENT u **ADMIN** (un admin que cambió el status del cliente desde el
  panel queda registrado como autor). Mostrar `authorName` y, si difiere
  del agente actual, indicarlo (icono/etiqueta de rol).

---

## 8. Implicaciones para la app — checklist

### 8.1 Modelo de dominio

- [ ] Reemplazar `ClientStatus` por la lista de 8 valores (añadir
      `IN_PROGRESS`, renombrar `INVALID_NUMBER` → `UNREACHABLE`).
- [ ] Reemplazar `CallOutcome` por la lista de 7 verb-form values.
- [ ] Añadir `InterestLevel { COLD, WARM, HOT }`.
- [ ] Añadir `interestLevel: InterestLevel?` y `wrongNumberCount: Int` al
      data class `Client`.
- [ ] Si hay deserializadores Moshi/Gson/kotlinx.serialization con
      `@Json(name = …)` o equivalente, asegurarse de que los nombres
      coincidan exactamente con los strings del enum del backend.

### 8.2 Persistencia local (Room/SQLite)

- [ ] Migración: añadir columnas `interestLevel` (TEXT nullable) y
      `wrongNumberCount` (INTEGER NOT NULL DEFAULT 0) en la tabla de
      clientes.
- [ ] Migración del enum `ClientStatus`: si se persiste como string, los
      valores existentes `INVALID_NUMBER` deben renombrarse a
      `UNREACHABLE` (o limpiarse si es seguro). Considerar bumpear
      schema version y borrar la cache local — no hay datos de producción
      que perder.
- [ ] Migración del enum `CallOutcome`: igual. Si quedaron rows con valores
      viejos en colas pendientes de sync, el backend va a rechazar el
      payload (validación class-validator). Mejor limpiar la cola
      pendiente como parte de la migración o mapear:
      - `INTERESTED` → `ANSWERED_INTERESTED`
      - `NOT_INTERESTED` → `ANSWERED_NOT_INTERESTED`
      - `SOLD` → `ANSWERED_SOLD`
      - `INVALID_NUMBER` → `WRONG_NUMBER`
      - `NO_ANSWER`, `BUSY` → sin cambio

### 8.3 UI

- [ ] **Pantalla de outcome (fin de llamada):** actualizar los 7 botones
      con los nuevos valores. Considerar agrupar visualmente:
      contestó (4 botones `ANSWERED_*`) vs no contestó (3 botones).
- [ ] **Lista de clientes / cola activa:** añadir `IN_PROGRESS` al mapa de
      colores y labels. Renombrar `INVALID_NUMBER` → `UNREACHABLE`.
- [ ] **Detalle del cliente:** mostrar `interestLevel` cuando aplique;
      mostrar warning de `wrongNumberCount > 0`.
- [ ] **Acción "Cambiar status sin llamada":** botón dentro del detalle
      que abre un sheet/diálogo con selector de status, razón
      (opcional/requerida según el target), y selector de nivel cuando
      target es `INTERESTED`. Llama a
      `POST /clients/:id/agent-status-change`.
- [ ] **Acción rápida de nivel:** segmented control `Frío / Tibio /
      Caliente` visible cuando el cliente está en `INTERESTED`. Llama a
      `PATCH /clients/:id/interest-level`.

### 8.4 Capa de red (Retrofit/Ktor)

- [ ] Añadir endpoint `agentStatusChange(id, body)` en el `ClientApi`.
- [ ] Añadir endpoint `updateInterestLevel(id, body)` en el `ClientApi`.
- [ ] Verificar que el adaptador de enum del cliente HTTP serializa los
      nuevos valores tal cual (sin `lowerCase`, sin transformación).

### 8.5 Limpieza de docs (al terminar)

- [ ] Actualizar `docs/OVERVIEW.md` con el nuevo modelo de estados.
- [ ] Actualizar `docs/ARCHITECTURE.md` (capa de dominio, enums).
- [ ] Actualizar `docs/BACKEND_COORDINATION.md` con los endpoints nuevos
      y los valores de outcome actualizados.
- [ ] Revisar `docs/CLIENT_DISMISSAL.md` — el flow de dismissal sigue
      funcionando igual; sólo confirmar que no se contradiga con el nuevo
      modelo.
- [ ] Revisar `docs/CLIENTS_TAB_REDESIGN.md` y `docs/AGENT_UX_BACKLOG.md`
      por si mencionan los outcomes o status viejos.
- [ ] **Borrar este archivo.**

---

## 9. Out of scope (no implementar todavía)

- Vista del **historial completo de status** (`GET /clients/:id/status-history`)
  — está pensada para el panel admin. La app puede consumirla en el futuro
  si hace falta, pero no es parte de este refactor.
- Permitir al agente **editar o borrar** entradas de `ClientStatusChange` —
  la colección es estrictamente append-only.
- Cambiar el `WRONG_NUMBER_THRESHOLD` desde la app — sigue siendo variable
  de entorno del backend (default 3).
- Capturar `interestLevel` directamente en la pantalla de outcome de
  llamada — el DTO de sync no lo soporta hoy. Si en el futuro se decide
  añadirlo, será un cambio de backend (DTO + service) y luego de mobile.
