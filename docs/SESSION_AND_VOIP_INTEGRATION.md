# Backend Integration — Sessions + VoIP

> **Para el agente que tome este ticket:** este documento es **autocontenido**.
> Resume los **dos cambios** del backend `calls-core` (2026-05-03) que el SDK
> Android tiene que aplicar.
>
> **Solo aplica al login del AGENT.** El admin NO usa esta app — se loguea
> en el panel web. No hay flujo de admin en mobile.

> **Estado:** backend ya deployed con estos cambios. La app actual ya no
> puede loguearse hasta que se aplique el cambio del §1.

---

## 1. 🔴 Login del agente — ahora requiere `device` y maneja sesión única

### 1.1 Body del login

**Antes:**
```http
POST /api/auth/login
{ "email": "...", "password": "..." }
```

**Ahora:**
```http
POST /api/auth/login
{
  "email": "agent1@test.com",
  "password": "test1234",
  "device": {
    "brand": "Samsung",         // Build.MANUFACTURER
    "model": "Galaxy Tab A8",   // Build.MODEL
    "osVersion": "Android 14",  // "Android " + Build.VERSION.RELEASE
    "deviceType": "TABLET"      // MOBILE | TABLET | OTHER (heurística por screen size)
  }
}
```

Sin `device` → `400 BadRequest`.

```kotlin
data class DeviceInfo(
    val brand: String,
    val model: String,
    val osVersion: String,
    val deviceType: String,    // "MOBILE" | "TABLET" | "OTHER"
)

fun captureDeviceInfo(context: Context): DeviceInfo = DeviceInfo(
    brand = Build.MANUFACTURER ?: "Unknown",
    model = Build.MODEL ?: "Unknown",
    osVersion = "Android ${Build.VERSION.RELEASE}",
    deviceType = if (context.resources.configuration.smallestScreenWidthDp >= 600) "TABLET" else "MOBILE",
)
```

### 1.2 Sesión única por agente — manejo del 401

El backend **invalida automáticamente la sesión anterior** cuando el agente
se loguea desde otro device, o cuando el admin la revoca, o cuando el
agente hace logout en otro lado. El JWT del device "viejo" deja de funcionar
al instante.

Cuando pasa, cualquier request autenticado responde:

```json
HTTP/1.1 401 Unauthorized

{
  "statusCode": 401,
  "message": "Session has been invalidated",
  "error": "UnauthorizedException"
}
```

**UX recomendada:** distinguir este 401 del 401 genérico (token expirado)
y mostrar copy diferenciado:

```kotlin
when {
    errorMessage?.contains("Session has been invalidated") == true ->
        showLogin(reason = "Tu sesión se cerró desde otro dispositivo o por el administrador. Vuelve a iniciar sesión.")
    else ->
        showLogin(reason = "Tu sesión expiró. Por favor inicia sesión nuevamente.")
}
```

---

## 2. 🟡 VoIP — fetch automático de credenciales

Después del login (y al volver del background), el SDK **debe** llamar
este endpoint para obtener las credenciales SIP del agente y configurar
el soft-phone solo. El agente nunca toca credenciales.

### 2.1 Endpoint

```http
GET /api/voip-accounts/me
Authorization: Bearer <token>
```

### 2.2 Response (éxito — `200`)

```json
{
  "data": {
    "_id": "65f...",
    "label": "tablet Dana",
    "did": "6749-9515",
    "provider": "VOZELIA",
    "sipUsername": "202-11435",
    "sipPassword": "4zT9Rfp4qdSRmu7E",
    "sipDomain": "cpbxa.vozelia.com.pa",
    "agentId": "65a...",
    "isActive": true
  },
  "statusCode": 200
}
```

> El backend usa envelope `{ data, statusCode }`. El payload está en
> `response.data`.

### 2.3 Configurar el cliente SIP

```kotlin
data class VoipAccount(
    val _id: String,
    val label: String?,
    val did: String,
    val provider: String,
    val sipUsername: String,
    val sipPassword: String,
    val sipDomain: String,
    val agentId: String?,
    val isActive: Boolean,
)

fun configureSipClient(account: VoipAccount) {
    sipEngine.register(
        username = account.sipUsername,
        password = account.sipPassword,
        domain = account.sipDomain,
        callerIdDisplay = account.did,
    )
}
```

> Los detalles de port/transport los maneja la librería SIP que estés
> usando con sus defaults. El backend ya no envía esos campos.

### 2.4 Cuándo llamarlo

| Trigger | Acción |
|---|---|
| Justo después de login exitoso | Configurar SIP |
| App vuelve del background | Reconfigurar (el admin pudo haber cambiado la cuenta) |
| Cada 2-4h en foreground | Refresh proactivo |

### 2.5 Response cuando el agente NO tiene cuenta asignada

```json
HTTP/1.1 404 Not Found

{
  "statusCode": 404,
  "message": "No active VoIP account assigned to this agent"
}
```

**Bloquear la cola de llamadas** y mostrar:
> "No tienes una cuenta VoIP asignada. Contacta al administrador para que te
> asigne una."

Reintentar al volver del background.

---

## 3. Test plan manual

1. Login con `agent1@test.com / test1234` desde device A → debe loguear OK,
   debe llamar `/voip-accounts/me`, registrar SIP automáticamente.
2. Hacer una llamada de prueba — debe salir por VoIP.
3. **Login en device B** con el mismo agente.
4. Device A → siguiente request debe dar 401 con `"Session has been
   invalidated"`. UI redirige a login con copy "Tu sesión se cerró desde
   otro dispositivo".
5. Desde el panel admin → revocar la sesión de device B con motivo "test".
6. Device B → siguiente request debe dar 401 con el mismo mensaje.
7. Login en device B otra vez → todo OK.

---

## 4. Refs

- Spec backend Sessions:
  [`calls-core/docs/SESSIONS.md`](../../calls-core/docs/SESSIONS.md)
- Postman con todos los endpoints (sección "Sessions" y "VoIP Accounts"):
  [`calls-core/docs/postman_collection.json`](../../calls-core/docs/postman_collection.json)
