# DIALER_SETUP_GUIDE — How to make `calls-agends` a default dialer

Step-by-step implementation playbook. Read
[`TELECOM_ARCHITECTURE.md`](./TELECOM_ARCHITECTURE.md) first — that's
the *what* and *why*. This is the *how*.

Last updated: **2026-04-25** (initial)

---

## Order of operations

There is a strict dependency order. Following it prevents a 2-day
debugging session of "why does the system ignore my dialer?".

```
1. Manifest declarations (services + intent-filters + permissions)
2. ConnectionService skeleton (CallsConnectionService)
3. InCallService skeleton (CallsInCallService)
4. CallManager singleton + DI wiring
5. Onboarding flow (permissions + role acquisition)
6. InCallActivity (Compose UI)
7. Wire PreCallScreen "Call" button to CallManager.startCall()
8. DisconnectCauseMapper
9. Incoming-call UI (Option B) + MissedCallEntity + DAO + ClientDao.findByPhone
10. Crash recovery (orphaned-call modal on launch)
```

Each step below maps to one of these. Don't skip ahead — without (1)
the OS doesn't know we're a dialer, without (3) the role grant works
but no call ever surfaces, etc.

---

## Step 1 — Manifest

### 1.1 Permissions

Add to `app/src/main/AndroidManifest.xml`, top level (above
`<application>`):

```xml
<!-- Dialer-required runtime permissions -->
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Manifest-only declarations -->
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
```

### 1.2 Activity intent-filters

`MainActivity` already has `MAIN/LAUNCHER`. Add the dialer ones so the
system lists us when the user opens the role chooser:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Dialer eligibility -->
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="tel" />
    </intent-filter>
</activity>
```

### 1.3 OnboardingActivity

Declared as a non-history, lock-screen-friendly activity. Goes inside
`<application>`:

```xml
<activity
    android:name=".presentation.onboarding.OnboardingActivity"
    android:exported="false"
    android:noHistory="true"
    android:excludeFromRecents="true"
    android:launchMode="singleTask"
    android:theme="@style/Theme.CallsAgent.Onboarding" />
```

### 1.4 InCallActivity

Lock-screen friendly so the agent can take a call when the device is
locked or sleeping:

```xml
<activity
    android:name=".presentation.incall.InCallActivity"
    android:exported="false"
    android:launchMode="singleTask"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:screenOrientation="landscape"
    android:theme="@style/Theme.CallsAgent.InCall" />
```

### 1.5 ConnectionService

```xml
<service
    android:name=".telecom.CallsConnectionService"
    android:exported="true"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.ConnectionService" />
    </intent-filter>
</service>
```

### 1.6 InCallService

```xml
<service
    android:name=".telecom.CallsInCallService"
    android:exported="true"
    android:permission="android.permission.BIND_INCALL_SERVICE">
    <meta-data
        android:name="android.telecom.IN_CALL_SERVICE_UI"
        android:value="true" />
    <meta-data
        android:name="android.telecom.IN_CALL_SERVICE_RINGING"
        android:value="false" />
    <intent-filter>
        <action android:name="android.telecom.InCallService" />
    </intent-filter>
</service>
```

> **Why `exported="true"`?** Because the system Telecom service (a
> different process) binds these. Without `exported`, the bind fails
> silently. The `permission` attribute restricts who can bind to "only
> something holding `BIND_*_SERVICE`", which only the system has.

---

## Step 2 — `CallsConnectionService` skeleton

```kotlin
package com.project.vortex.callsagent.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class CallsConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        return OutgoingConnection().apply {
            setInitializing()
            setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
            // Set active immediately — the radio/network then drives state changes.
            setActive()
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        // Option B: accept the connection so it rings — UI handles
        // accept/reject. If another call is already active, return a
        // pre-disconnected BUSY connection (see TELECOM_ARCHITECTURE § 6.1).
        if (callManager.hasActiveCall()) {
            callManager.logIncomingMissed(
                phoneNumber = request?.address?.schemeSpecificPart.orEmpty(),
                reason = MissedCallReason.BUSY_OTHER_CALL,
            )
            return Connection().apply {
                setDisconnected(DisconnectCause(DisconnectCause.BUSY))
                destroy()
            }
        }
        return IncomingConnection().apply {
            setRinging()
            setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        }
    }

    private inner class OutgoingConnection : Connection() {
        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }
        override fun onAbort() {
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
        }
        override fun onHold() = setOnHold()
        override fun onUnhold() = setActive()
    }

    private inner class IncomingConnection : Connection() {
        override fun onAnswer() = setActive()
        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        }
        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }
    }
}
```

---

## Step 3 — `CallsInCallService` skeleton

```kotlin
package com.project.vortex.callsagent.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.project.vortex.callsagent.presentation.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CallsInCallService : InCallService() {

    @Inject lateinit var callManager: CallManager

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val isIncoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        if (isIncoming) {
            // Belt-and-suspenders: ConnectionService already rejected this
            // path, but if it ever leaks here, terminate and log.
            callManager.logIncomingRejected(call)
            call.disconnect()
            return
        }

        callManager.setCall(call, this)
        setAudioRoute(CallAudioState.ROUTE_SPEAKER)

        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callManager.onCallEnded(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        callManager.onAudioStateChanged(audioState)
    }
}
```

---

## Step 4 — `CallManager` (singleton)

```kotlin
package com.project.vortex.callsagent.telecom

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.telecom.model.CallUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    // Repositories injected here when wired (interactionRepository, noteRepository, missedCallRepository, etc.)
) {
    private var currentCall: Call? = null
    private var inCallService: InCallService? = null

    private val _currentClient = MutableStateFlow<Client?>(null)
    val currentClient: StateFlow<Client?> = _currentClient.asStateFlow()

    private val _callState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callState: StateFlow<CallUiState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    val liveNoteContent = MutableStateFlow("")

    fun startCall(client: Client) {
        _currentClient.value = client
        _callState.value = CallUiState.Dialing
        liveNoteContent.value = ""

        val telecom = appContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", client.phone, null)
        telecom.placeCall(uri, null)
    }

    fun setCall(call: Call, ctx: InCallService) {
        currentCall = call
        inCallService = ctx
        call.registerCallback(callCallback)
        updateStateFromCall(call)
    }

    fun mute(enabled: Boolean) {
        inCallService?.setMuted(enabled)
        _isMuted.value = enabled
    }

    fun setSpeaker(enabled: Boolean) {
        val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        inCallService?.setAudioRoute(route)
        _isSpeakerOn.value = enabled
    }

    fun disconnect() {
        currentCall?.disconnect()
    }

    fun onAudioStateChanged(state: CallAudioState) {
        _isMuted.value = state.isMuted
        _isSpeakerOn.value = state.route == CallAudioState.ROUTE_SPEAKER
    }

    fun onCallEnded(call: Call) {
        // 1. Persist InteractionEntity (callStartedAt, callEndedAt, duration, disconnectCause)
        // 2. If liveNoteContent.isNotBlank() → persist NoteEntity(type = CALL)
        // 3. Compute prefilled outcome via DisconnectCauseMapper
        // 4. Emit one-shot event to navigate PostCallScreen
        currentCall?.unregisterCallback(callCallback)
        currentCall = null
        inCallService = null
        _callState.value = CallUiState.Disconnected
    }

    fun logIncomingRejected(call: Call) {
        // Persist MissedCallEntity. Implementation lives in MissedCallRepository.
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateStateFromCall(call)
        }
    }

    private fun updateStateFromCall(call: Call) {
        _callState.value = when (call.state) {
            Call.STATE_DIALING -> CallUiState.Dialing
            Call.STATE_RINGING -> CallUiState.Ringing
            Call.STATE_ACTIVE -> CallUiState.Active
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallUiState.Disconnected
            else -> _callState.value // hold/connecting transitional states ignored
        }
    }
}
```

`CallUiState` sealed class:

```kotlin
sealed class CallUiState {
    object Idle : CallUiState()
    object Dialing : CallUiState()
    object Ringing : CallUiState()
    object Active : CallUiState()
    object Disconnected : CallUiState()
}
```

---

## Step 5 — Onboarding flow

`OnboardingActivity` is a separate Activity (not a Compose destination
inside the main NavGraph). It's the only screen the agent can see if
any requirement is missing. The user can NOT bypass it — back button
disabled, recents excluded.

### 5.1 `MainActivity` gate

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsPreferences: SettingsPreferences
    @Inject lateinit var onboardingGate: OnboardingGate

    override fun onResume() {
        super.onResume()
        if (!onboardingGate.allRequirementsMet()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setContent
    }
}
```

### 5.2 `OnboardingGate`

```kotlin
@Singleton
class OnboardingGate @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun allRequirementsMet(): Boolean =
        isDialerRoleHeld() &&
        hasPermission(Manifest.permission.CALL_PHONE) &&
        hasPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) &&
        hasNotificationPermission() &&
        isBatteryOptimizationIgnored()

    fun isDialerRoleHeld(): Boolean {
        val rm = context.getSystemService(RoleManager::class.java)
        return rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        else true

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
```

### 5.3 OnboardingScreen — step list

5 steps, each with: title, explainer, status indicator, action button.
The button changes label based on state:

| Step | Action button when missing | When granted |
|---|---|---|
| 1. Default dialer | "Set as default dialer" → `RoleManager.createRequestRoleIntent` | "✓ Active" |
| 2. Phone permission | "Allow" → `requestPermissions(CALL_PHONE)` | "✓ Granted" |
| 3. Audio permission | "Allow" → `requestPermissions(MODIFY_AUDIO_SETTINGS)` | "✓ Granted" |
| 4. Notifications (API 33+) | "Allow" → `requestPermissions(POST_NOTIFICATIONS)` | "✓ Granted" |
| 5. Battery optimization | "Allow background" → `Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$pkg")` | "✓ Allowed" |

Each step's `onResume` re-checks status. When all 5 are green, a
"Continue" button appears, finishing `OnboardingActivity` and
re-launching `MainActivity`.

### 5.4 Hard-deny handling

If a permission was denied with "Don't ask again":

```kotlin
val canRequest = ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
if (!canRequest && !hasPermission(perm)) {
    // Hard-denied — change the action button to "Open Settings"
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}
```

The agent has to manually toggle it in Settings, then return to the
app. `onResume` re-checks and the step turns green.

### 5.5 Back button blocked

```kotlin
override fun onBackPressed() {
    // Intentional no-op. The agent must complete onboarding to use the app.
}
```

Combined with `noHistory="true"` and `excludeFromRecents="true"` in
the manifest, there's no path out without granting.

---

## Step 6 — `InCallActivity` (Compose UI)

Lock-screen friendly. Shows the current client, live timer, notes
field, mute/speaker toggles, end-call button.

```kotlin
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent {
            CallsAgendsTheme {
                InCallScreen()
            }
        }
    }
}
```

`InCallScreen` consumes `CallManager` state via `hiltViewModel()` and
the `InCallViewModel` exposes `callState`, `isMuted`, `isSpeakerOn`,
`liveNoteContent`. UI layout:

- Top: client name (large) + phone + status pill (Dialing / Ringing /
  Active / Disconnected).
- Center-left: live timer (counts seconds from `STATE_ACTIVE`).
- Center-right: large multi-line `OutlinedTextField` bound to
  `liveNoteContent`. Stays visible the entire call.
- Bottom action row: Mute toggle, Speaker toggle, End Call (red
  circular button).

When `callState` flips to `Disconnected`, the activity finishes and the
parent navigation lands on `PostCallScreen` with the new
`interactionMobileSyncId`.

---

## Step 7 — Wire `PreCallScreen` Call button

Replace the `Intent.ACTION_DIAL` shim with `CallManager.startCall(client)`:

```kotlin
// In PreCallScreen.kt CallActionBar:
val callManager: CallManager = hiltViewModel<CallActionViewModel>().callManager

Button(
    onClick = {
        val client = uiState.client ?: return@Button
        callManager.startCall(client)
        // No navigation here — InCallActivity is launched by InCallService.
    },
    // ...existing styling...
)
```

The `Intent.ACTION_DIAL` workaround introduced in Phase 1 is removed.
The agent never sees the OEM dialer.

---

## Step 8 — `DisconnectCauseMapper`

```kotlin
object DisconnectCauseMapper {
    fun toOutcome(cause: DisconnectCause?): CallOutcome? = when (cause?.code) {
        null -> null
        DisconnectCause.LOCAL -> null            // agent hung up — let agent pick
        DisconnectCause.REMOTE -> null           // client hung up — let agent pick
        DisconnectCause.BUSY -> CallOutcome.BUSY
        DisconnectCause.CANCELED -> CallOutcome.NO_ANSWER
        DisconnectCause.MISSED -> CallOutcome.NO_ANSWER
        DisconnectCause.REJECTED -> CallOutcome.NOT_INTERESTED
        DisconnectCause.ERROR -> mapErrorSubcause(cause)
        else -> null
    }

    private fun mapErrorSubcause(cause: DisconnectCause): CallOutcome? {
        // Best-effort — Telecom doesn't expose stable subcodes for ERROR.
        // Refine once we have real-world data.
        val reason = cause.reason?.lowercase().orEmpty()
        return when {
            "invalid_number" in reason -> CallOutcome.INVALID_NUMBER
            "busy" in reason -> CallOutcome.BUSY
            else -> null
        }
    }
}
```

The mapped outcome is passed as `prefilledOutcome` nav arg to
`PostCallScreen`. Agent can override.

---

## Step 9 — Incoming-call UI (Option B) + missed-call log

### 9.1 Schema additions

`InteractionEntity` gets a new column. Update the entity + bump Room
schema version (or destructive recreate — pre-prod):

```kotlin
enum class CallDirection { OUTBOUND, INBOUND }

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey val mobileSyncId: String,
    val clientId: String,
    val direction: CallDirection = CallDirection.OUTBOUND,  // NEW
    val callStartedAt: Instant,
    val callEndedAt: Instant,
    val durationSeconds: Int,
    val outcome: CallOutcome,
    val disconnectCause: String?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
```

`MissedCallEntity` with reason discriminator:

```kotlin
enum class MissedCallReason { REJECTED, NOT_ANSWERED, BUSY_OTHER_CALL }

@Entity(tableName = "missed_calls")
data class MissedCallEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val matchedClientId: String?,
    val reason: MissedCallReason,
    val occurredAt: Instant,
    val acknowledged: Boolean = false,
)
```

DAO:

```kotlin
@Dao
interface MissedCallDao {
    @Insert suspend fun insert(entity: MissedCallEntity)
    @Query("SELECT * FROM missed_calls WHERE acknowledged = 0 ORDER BY occurredAt DESC")
    fun observeUnacknowledged(): Flow<List<MissedCallEntity>>
    @Query("UPDATE missed_calls SET acknowledged = 1 WHERE id = :id")
    suspend fun markAcknowledged(id: String)
}
```

### 9.2 Phone-number lookup

`ClientDao` gets a normalized lookup method:

```kotlin
@Dao
interface ClientDao {
    // ... existing methods ...

    /** Match by last 8 digits of phone (handles country-code prefix variations). */
    @Query("""
        SELECT * FROM clients
        WHERE substr(replace(replace(replace(phone, ' ', ''), '-', ''), '+', ''), -8)
            = substr(replace(replace(replace(:phone, ' ', ''), '-', ''), '+', ''), -8)
        LIMIT 1
    """)
    suspend fun findByNormalizedPhone(phone: String): ClientEntity?
}
```

### 9.3 `CallManager` additions

```kotlin
class CallManager @Inject constructor(...) {
    // ... existing fields ...

    private val _callDirection = MutableStateFlow(CallDirection.OUTBOUND)
    val callDirection: StateFlow<CallDirection> = _callDirection.asStateFlow()

    fun hasActiveCall(): Boolean = currentCall != null

    fun onIncomingCall(call: Call, ctx: InCallService) {
        val phone = call.details.handle?.schemeSpecificPart.orEmpty()
        scope.launch {
            val matchedClient = clientRepository.findByPhone(phone)
            _currentClient.value = matchedClient
            _callDirection.value = CallDirection.INBOUND
            currentCall = call
            inCallService = ctx
            call.registerCallback(callCallback)
            updateStateFromCall(call)
            // InCallActivity is launched by the InCallService — UI sees
            // direction = INBOUND and switches into ringing mode.
        }
    }

    fun acceptIncoming() {
        currentCall?.answer(0)
        inCallService?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
    }

    fun rejectIncoming() {
        currentCall?.disconnect()
        scope.launch {
            logIncomingMissed(
                phoneNumber = currentCall?.details?.handle?.schemeSpecificPart.orEmpty(),
                reason = MissedCallReason.REJECTED,
            )
        }
    }

    fun logIncomingMissed(phoneNumber: String, reason: MissedCallReason) {
        scope.launch {
            val match = clientRepository.findByPhone(phoneNumber)
            missedCallRepository.insert(
                MissedCallEntity(
                    id = UUID.randomUUID().toString(),
                    phoneNumber = phoneNumber,
                    matchedClientId = match?.id,
                    reason = reason,
                    occurredAt = Instant.now(),
                ),
            )
        }
    }
}
```

### 9.4 `CallsInCallService.onCallAdded` — direction split

```kotlin
override fun onCallAdded(call: Call) {
    super.onCallAdded(call)

    val isIncoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
    if (isIncoming) {
        callManager.onIncomingCall(call, this)
        // Don't force speaker yet — call is still ringing. Speaker is
        // applied on Accept.
    } else {
        callManager.setCall(call, this)
        setAudioRoute(CallAudioState.ROUTE_SPEAKER)
    }

    val intent = Intent(this, InCallActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(intent)
}
```

### 9.5 `InCallScreen` — incoming ringing mode

Branch in the Compose tree on `direction == INBOUND && callState == Ringing`:

```kotlin
@Composable
fun InCallScreen(viewModel: InCallViewModel = hiltViewModel()) {
    val callState by viewModel.callState.collectAsState()
    val direction by viewModel.callDirection.collectAsState()
    val client by viewModel.currentClient.collectAsState()
    val phone by viewModel.callerPhone.collectAsState()

    when {
        direction == CallDirection.INBOUND && callState is CallUiState.Ringing -> {
            IncomingRingingUi(
                client = client,
                phoneNumber = phone,
                onAccept = viewModel::acceptIncoming,
                onReject = viewModel::rejectIncoming,
            )
        }
        else -> ActiveCallUi(
            isIncoming = direction == CallDirection.INBOUND,
            // ... rest of existing UI ...
        )
    }
}

@Composable
private fun IncomingRingingUi(
    client: Client?,
    phoneNumber: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Top: "Incoming call" badge in error/warning color
        Text("INCOMING CALL", style = MaterialTheme.typography.labelLarge)

        Spacer(Modifier.weight(1f))

        // Center: avatar + name + status pill
        if (client != null) {
            Avatar(name = client.name, size = 96.dp)
            Text(client.name, style = MaterialTheme.typography.headlineMedium)
            StatusPill(label = client.status.label(), palette = client.status.palette())
        } else {
            Avatar(name = "?", size = 96.dp)
            Text("Unknown number", style = MaterialTheme.typography.headlineMedium)
        }
        Text(phoneNumber, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.weight(1f))

        // Bottom: oversized accept (green) + reject (red) buttons
        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            CircularActionButton(icon = CallEnd, color = Red, onClick = onReject)
            CircularActionButton(icon = Call, color = PhoneGreen, onClick = onAccept)
        }
    }
}
```

When the agent taps Accept, `callState` flips to `Active` and the
composable swaps to `ActiveCallUi(isIncoming = true)` — same UI as
outbound but with a persistent "Incoming" badge in the header.

### 9.6 Caller hangs up while ringing (not-answered)

`InCallService.onCallRemoved` handles this. If the call was removed
while in `STATE_RINGING` and direction is INBOUND, log it as
`NOT_ANSWERED`:

```kotlin
override fun onCallRemoved(call: Call) {
    super.onCallRemoved(call)
    val wasRinging = call.state == Call.STATE_RINGING ||
        callManager.callState.value is CallUiState.Ringing
    if (wasRinging && callManager.callDirection.value == CallDirection.INBOUND) {
        callManager.logIncomingMissed(
            phoneNumber = call.details.handle?.schemeSpecificPart.orEmpty(),
            reason = MissedCallReason.NOT_ANSWERED,
        )
    }
    callManager.onCallEnded(call)
}
```

### 9.7 Persisting an answered incoming call

`CallManager.onCallEnded` already creates an `InteractionEntity` for
ended calls. With direction support added in 9.1, set
`direction = _callDirection.value` so inbound interactions carry the
right tag through sync.

### 9.8 Missed-call banner in Clients tab

`ClientsViewModel` exposes a flow:

```kotlin
val unacknowledgedMissedCalls: StateFlow<List<MissedCallWithClient>> =
    missedCallRepository.observeUnacknowledged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Above the search field, when non-empty:

```
⚠ 3 missed calls — Tap to review
```

Tap → `ModalBottomSheet` with the list. Each row:

- Avatar + matched client name OR "Unknown number"
- Phone number
- Reason badge ("Rejected" / "Missed" / "Busy")
- Time-ago label
- Right-side: `Call back` button (green, calls outbound flow) and
  `Dismiss` button (marks acknowledged).

If `matchedClientId == null`, "Call back" first prompts:
*"Number not in your queue — call anyway?"* before placing the call.

---

## Step 10 — Crash recovery

In `MainActivity.onCreate`, after onboarding gate passes:

```kotlin
viewModel.checkOrphanedCall { orphan ->
    if (orphan != null) {
        // Show modal: "You had a call with X that didn't finish. Record outcome?"
        // → opens PostCallScreen with the orphan interactionMobileSyncId.
    }
}
```

`checkOrphanedCall` queries `InteractionDao` for the most recent row
with `callStartedAt` set but `callEndedAt == null`. If one exists and
it's > 60 seconds old (i.e., we crashed, not just navigated), surface
the modal.

---

## Testing on a real Tab A9+

### Pre-flight checklist

1. SIM card present, has airtime, has data (for sync).
2. `adb shell dumpsys role` → confirm we're listed under
   `android.app.role.DIALER`.
3. `adb shell dumpsys telecom | grep -A5 "Default Dialer"` → confirm
   our package name appears.
4. Settings → Apps → Default apps → Phone app → confirm we are selected.

### Manual test plan

| # | Action | Expected |
|---|---|---|
| 1 | Fresh install, login | Onboarding launches, all 5 steps red |
| 2 | Tap each step, grant permission | Step turns green; "Continue" button appears only when all 5 green |
| 3 | Tap Continue | MainActivity loads, Clients tab shows queue |
| 4 | Open client, tap Call | Within 1s: InCallActivity launches in landscape, status "Dialing" |
| 5 | Call connects | Status changes to "Active", timer starts ticking |
| 6 | Type notes during call | Text appears in textarea, persists across orientation |
| 7 | Tap End Call | Activity closes, PostCallScreen opens with disconnect-cause-mapped outcome pre-selected |
| 8 | Force-stop the app mid-call | OS keeps call alive, our UI is gone — relaunch shows orphaned-call modal |
| 9 | Settings → revoke dialer role manually | Re-launching app sends to Onboarding, blocking entry |
| 10 | Have someone call the SIM (no active call) | InCallActivity opens in landscape with "Incoming call" + matched client name (if assigned) or "Unknown number". |
| 11 | Tap Accept | Call connects, speaker on, "Incoming" badge visible in header. Agent can type notes. End → PostCallScreen with `direction=INBOUND` and outcome prefilled by DisconnectCauseMapper. |
| 12 | Tap Reject during ring | Caller hears reject tone. MissedCallEntity inserted with `reason=REJECTED`. Banner appears in Clients tab. |
| 13 | Caller hangs up before agent answers | MissedCallEntity inserted with `reason=NOT_ANSWERED`. |
| 14 | Incoming arrives while another call is active | New caller hears busy. MissedCallEntity inserted with `reason=BUSY_OTHER_CALL`. The active call is undisturbed. |
| 15 | Tap missed-call banner → list → "Call back" (matched) | Opens PreCallScreen for that client. After call back, the missed-call entry is marked acknowledged. |
| 16 | Tap "Call back" (unmatched) | Confirmation prompt "Number not in your queue — call anyway?". Confirm → outbound call placed. |

### `adb` cheats for dev

```bash
# See all telecom-bound services
adb shell dumpsys telecom

# Trigger an incoming call (emulator only — real Tab A9+ needs another phone)
adb emu gsm call +50712345678

# Check our role status
adb shell cmd role get-role-holders android.app.role.DIALER

# Force grant the role (debug only, requires root or system app)
adb shell cmd role add-role-holder android.app.role.DIALER com.project.vortex.callsagent
```

---

## Common pitfalls

### "I granted everything but `onCallAdded` never fires"

- Verify the `InCallService` declaration has both `meta-data` entries.
  `IN_CALL_SERVICE_UI=true` is the magic flag — without it the system
  binds the service for state notifications but never delivers the UI
  call.
- Check `dumpsys telecom` — if it lists our service under "InCall
  Components" but our role is *not* the active dialer, we won't
  receive calls. Re-trigger the role assignment.

### "The system OEM dialer keeps showing up"

- Re-check `RoleManager.isRoleHeld(ROLE_DIALER)` → if false, the role
  reverted somehow.
- On Samsung, sometimes after an OEM update the role resets. Add a
  defensive `onResume` re-check in `MainActivity`.

### "VoLTE is disabled / call quality is bad"

- Carrier-side. Some operators in Panama gate VoLTE to system dialers
  only. Mitigation: ensure SIM has VoLTE provisioned by the carrier
  on the corporate plan. There is no app-side fix.
- Verify with `adb shell dumpsys telephony.registry | grep -i volte`.

### "The InCallActivity doesn't show on top of lock screen"

- Confirm `setShowWhenLocked(true)` and `setTurnScreenOn(true)` are
  set in `onCreate`, not just declared in manifest (manifest
  declarations are deprecated since API 27).
- On some Samsung models you also need `KeyguardManager.requestDismissKeyguard()`
  if the device has a secure lock. Out of MVP scope.

### "Battery optimization keeps killing the InCallService"

- The `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` grant only applies to
  this specific app. Samsung One UI has a separate "Sleeping apps"
  list that may still intervene. For production deployment, add the
  app to the MDM allowlist directly. Settings → Battery →
  Background usage limits → Sleeping apps → remove `calls-agends`.

---

## Manifest-merge debugging

If the build doesn't include our service declarations after merge:

```bash
./gradlew :app:processDebugManifest
cat app/build/intermediates/merged_manifest/debug/AndroidManifest.xml | grep -A 3 "CallsConnectionService\|CallsInCallService"
```

Should show both services with their `intent-filter`s. If missing,
suspect a wrong `tools:node` directive or duplicate declaration in a
library module.
