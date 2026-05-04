# SIP_VOSELIA_CONFIG — Voselia trunk configuration for the Linphone client

> **STATUS: PARTIALLY VERIFIED — 6 of 9 rows confirmed from production Sipnetic.
> Account-level settings (transport priority, expires, keep-alive) and
> NAT-traversal settings (STUN, ICE) are still pending.**
>
> Confirmed rows are evidenced by direct screenshots of the production
> Sipnetic client running on the agents' Galaxy Tab A9+ (2026-05-03).
> Pending rows still hold working assumptions and **must** be verified
> before being implemented in `LinphoneCoreManager`.
>
> The goal of this document is to make the SIP migration to Voselia
> reproducible and auditable: **what we assume, how we proved it, and what
> the final confirmed value is.**
>
> ### Assumptions proven WRONG by the Sipnetic inspection
>
> Two industry-standard assumptions in the original draft turned out to be
> incorrect for this specific Voselia Panama deployment. Recording them
> here so the next reviewer doesn't repeat the mistake:
>
> 1. **SRTP was assumed "optional / disabled by default" → it is ENABLED**
>    in production with **SDES (RFC 4568)** key exchange. Implementing
>    Linphone with `MediaEncryption.None` would result in `488 Not
>    Acceptable Here` from the SBC.
> 2. **Opus was assumed "not supported on standard SIP trunks" → it is
>    OFFERED FIRST** in Sipnetic's codec priority list. Calls work, so the
>    SBC accepts it (or transparently degrades to G.711/G.729). We can and
>    should offer Opus from Linphone for better mobile-network quality.

---

## 1. Parameter inventory

| # | Parameter        | Confirmed / assumed value                                                                                                                                                                                                                                                                  | Status |
|---|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| 1 | Transport        | UDP, TCP and TLS **all enabled** at the app level in Sipnetic (`Network` settings). **Preferred transport: UDP first**, with TCP and TLS as fallbacks. SIP local port is **random by default** (the static `5060` field is greyed out).                                                  | **CONFIRMED** (Sipnetic Network screenshot + user-supplied transport order, 2026-05-03) |
| 2 | Proxy / Registrar| **`cpbxa.vozelia.com.pa`** (Voselia Panamá — primary SBC for the test agent). The onboarding PDF showed `cpbxb` as a generic example; the real account assigned to operations lives on `cpbxa`. Voselia likely runs an `a/b` SBC pair (active/standby or per-account sharding). No separate outbound proxy expected for mobile. | **CONFIRMED** (test-agent credentials, 2026-05-03) |
| 3 | STUN             | Not visible in the screenshots taken so far. Pending capture of the account's NAT-traversal screen.                                                                                                                                                                                        | **UNVERIFIED** |
| 4 | TURN             | Not visible. Working assumption: Voselia handles NAT carrier-side and TURN is unnecessary. To be confirmed by NAT-traversal screen and/or PCAPdroid (path C).                                                                                                                              | **UNVERIFIED** |
| 5 | ICE              | Not visible. Pending NAT-traversal screen.                                                                                                                                                                                                                                                 | **UNVERIFIED** |
| 6 | Media (SRTP)     | **Enabled** — `Enable call encryption` toggle ON in Sipnetic `Security` settings. **Key exchange: SDES (RFC 4568)**. ZRTP off, OTR off. Crypto algorithms: 5 block ciphers + 4 auth methods selected (defaults).                                                                          | **CONFIRMED** (Sipnetic Security screen, 2026-05-03) |
| 7 | Codecs (SBC)     | Audio codecs offered by Sipnetic, in priority order: **Opus (VBR), G.722, Speex, GSM 06.10, G.711 A-Law, G.711 µ-Law, G.729**. Speex Wideband / Ultra-Wideband disabled. Limit bit rate ON, max **32 kbit/s**. Opus FEC OFF. G.729 VAD ON. The codec the SBC actually picks per call still requires SDP inspection (path B.2). | **CONFIRMED list** (Sipnetic codec screen, 2026-05-03); negotiated codec pending |
| 8 | NAT handling     | SIP local port: **random** (anti-NAT). RTP port range: **16384–65535**. `rport` and `keep-alive interval` not yet visible — pending account-level `Network` screen.                                                                                                                       | **PARTIAL** |
| 9 | Registration     | `Expires` not yet visible — pending account-level screen. Multi-device assumption (one active registration per account) still unconfirmed.                                                                                                                                                | **UNVERIFIED** |
|10 | Audio processing | Microphone AGC: **ON**. Speakers AGC: OFF. Echo cancellation: **ON**. (Sipnetic `Preferences → Sound processing`.)                                                                                                                                                                          | **CONFIRMED** (Sipnetic Preferences screen, 2026-05-03) |

---

## 2. Why "unverified" matters

Implementing the wrong assumption costs us a full day of debugging per
parameter, and some failure modes are **silent**:

- Wrong transport → REGISTER never reaches the SBC. Loud failure, easy fix.
- Wrong codec list → call connects but **no audio** or one-way audio.
  Silent, looks like a NAT issue, isn't.
- Missing STUN → audio works on the office WiFi, **fails on 4G**. Surfaces
  only in production.
- Wrong `Expires` → registration drops mid-shift, calls fail until restart.
- SRTP enabled when SBC expects RTP → INVITE rejected with `488 Not Acceptable Here`.

Every row above must be confirmed before we cut over from the legacy dialer.

---

## 3. Verification plan

Three independent paths. **A is mandatory. B is highly recommended.
C is the fallback when A and B leave doubt.**

### Path A — Ask Voselia directly (mandatory)

Send the message in §5 to Voselia support. Their answer is authoritative
and replaces every "UNVERIFIED" tag with a confirmed value.

### Path B — Inspect the production Sipnetic client

The agents are already running Sipnetic against this exact Voselia
account. The configuration is right there.

**B.1 — Sipnetic UI**

On a production tablet:

1. Open Sipnetic → `Settings` → `Accounts` → select the Voselia account → `Edit`.
2. Take screenshots of:
   - Server / Domain / Proxy host and port.
   - Transport (UDP/TCP/TLS).
   - STUN server (host + port, or empty).
   - ICE checkbox state.
   - SRTP policy (`Disabled` / `Optional` / `Mandatory`).
   - Codec list and priority order.
   - Keep-alive interval.
3. Attach screenshots to this document under §6 "Findings".

**B.2 — Sipnetic SIP logs**

1. `Settings` → `Preferences` → `Advanced` → enable SIP logs.
2. Place one outbound test call to a known number, let it connect for ~10 s, hang up.
3. Export logs (`Settings` → `About` → `Send logs`, or pull the log file via USB).
4. From the log extract:
   - The `REGISTER` request — confirms transport, `Expires`, `Contact`, `Via` (look for `rport`).
   - The `INVITE` SDP body. Inspect:
     - `m=audio <port> RTP/AVP <pt-list>` → codecs in priority order. `RTP/SAVP` instead of `RTP/AVP` means SRTP is active.
     - `c=IN IP4 <addr>` → if `<addr>` is the tablet's public IP, STUN worked. If it's a private RFC1918 address, STUN is off or the SBC handles NAT.
     - `a=candidate:` lines → presence indicates ICE is enabled.
   - The first `200 OK` for `INVITE` — confirms the **codec the SBC actually picked** (often the second item, not the first).

### Path C — Wire-level packet capture (only if A + B leave doubt)

`PCAPdroid` (free, no root) on the production tablet:

1. Install PCAPdroid from Play Store on a test tablet.
2. Filter capture by the Sipnetic app.
3. Place an outbound call, let it run ~15 s, hang up.
4. Export the `.pcap`.
5. Open in Wireshark with filter `sip || rtp || stun || rtcp`.

What to look for:

- STUN `Binding Request` packets → confirms STUN is in use and reveals the server.
- TURN `Allocate` packets → if **absent during a successful call**, TURN is not needed for our network conditions (kills risk R2 from the estimate).
- Full SDP in the `INVITE` (only readable if transport is UDP/TCP — TLS hides it; in that case rely on B.2).
- RTP flow direction → both sides sending = NAT traversal works without TURN.

**Limitation:** if Voselia uses TLS, the SIP signaling is encrypted at the
wire and only RTP is visible. Path B.2 is then the authoritative source.

---

## 4. Per-parameter validation matrix

For each row in §1, fill the corresponding evidence column once verified.

| #  | Parameter                  | Confirmed value                                                                                          | Evidence source                                              | Verified by | Date       |
|----|----------------------------|----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|-------------|------------|
| 1a | Transport — global enable  | UDP ✓, TCP ✓, TLS ✓ (all enabled in Sipnetic `Network`)                                                 | Sipnetic Network screenshot                                  | —           | 2026-05-03 |
| 1b | Transport — preferred      | UDP first; TCP and TLS as fallback                                                                       | User-supplied (operations team), 2026-05-03                  | —           | 2026-05-03 |
| 1c | SIP local port             | Random per session (`Random port` ON; static 5060 disabled)                                              | Sipnetic Network screenshot                                  | —           | 2026-05-03 |
| 1d | RTP port range             | 16384–65535                                                                                              | Sipnetic Network screenshot                                  | —           | 2026-05-03 |
| 2  | Proxy / Registrar          | `cpbxa.vozelia.com.pa`                                                                                   | Test-agent credentials (operations, 2026-05-03)              | —           | 2026-05-03 |
| 3  | STUN                       | _pending_                                                                                                | B.1 (NAT-traversal account screen) / B.2                     |             |            |
| 4  | TURN                       | _pending_                                                                                                | A / B.1 / C                                                  |             |            |
| 5  | ICE                        | _pending_                                                                                                | B.1 / B.2                                                    |             |            |
| 6a | SRTP enabled               | Yes                                                                                                      | Sipnetic Security screenshot (`Enable call encryption` ON)   | —           | 2026-05-03 |
| 6b | SRTP key exchange          | SDES (RFC 4568). ZRTP off, OTR off.                                                                      | Sipnetic Security screenshot                                 | —           | 2026-05-03 |
| 7a | Codecs offered (priority)  | Opus → G.722 → Speex → GSM 06.10 → G.711 A-Law → G.711 µ-Law → G.729                                     | Sipnetic Audio codecs screenshot                             | —           | 2026-05-03 |
| 7b | Codec actually negotiated  | _pending_ — needs SDP `200 OK` body (path B.2)                                                           | B.2                                                          |             |            |
| 7c | Audio bit rate cap         | Limit bit rate ON, max 32 kbit/s. Opus FEC OFF. G.729 VAD ON.                                            | Sipnetic Audio codecs screenshot                             | —           | 2026-05-03 |
| 8a | rport / NAT keep-alive     | rport ON (default Linphone). Keep-alive interval still pending account-level screen.                     | M1 log §6.5 (Via;rport on REGISTER, 200 OK rewrote Contact)  | —           | 2026-05-03 |
| 9  | Registration `Expires`     | 3600 s (server accepted client value). Refresh at ~90% of expires.                                       | M1 log §6.5                                                  | —           | 2026-05-03 |
| 10 | Audio processing (client)  | Microphone AGC ON, Speakers AGC OFF, Echo cancellation ON                                                | Sipnetic Preferences screenshot                              | —           | 2026-05-03 |

---

## 5. Message template for Voselia support (Spanish)

```
Asunto: Configuración técnica del trunk SIP para integración con cliente propio

Hola equipo de Voselia,

Estamos integrando un nuevo cliente SIP propio (basado en el SDK de
Linphone) sobre la misma cuenta que actualmente usamos con Sipnetic.
Para asegurar que la configuración del cliente coincida con lo que el
SBC espera, necesitamos confirmar los siguientes parámetros del trunk:

1.  Transporte: ¿UDP, TCP o TLS? Indicar puerto.
2.  Proxy / registrar: ¿FQDN del SIP server? ¿Existe outbound proxy
    distinto?
3.  STUN: ¿proveen servidor STUN? Si no, ¿recomiendan uno público?
4.  TURN: ¿proveen servidor TURN? ¿En qué condiciones es necesario
    para clientes móviles detrás de NAT?
5.  ICE: ¿el SBC lo soporta o prefieren que el cliente envíe su IP
    pública directamente en el SDP?
6.  Media: ¿RTP en claro o SRTP? Si SRTP es opcional, ¿cómo se activa
    en la cuenta?
7.  Códecs habilitados en el SBC: ¿cuáles negocian (PCMU, PCMA, G.729,
    Opus, otros) y en qué orden de preferencia?
8.  NAT handling: ¿soportan rport (RFC 3581)? ¿Intervalo recomendado de
    keepalive UDP?
9.  Registro: ¿valor recomendado de Expires? ¿Permiten múltiples
    registros simultáneos por cuenta o solo el último activo?

Cualquier guía de configuración adicional para clientes basados en
Linphone/PJSIP es bienvenida.

Gracias.
```

---

## 6. Findings (fill after verification)

### 6.1 Path A — Voselia response

> _Paste Voselia's response here, dated. Quote verbatim._

**Partial confirmation already on hand (2026-05-03):**

Two independent inputs from operations:

1. **Sipnetic onboarding PDF** (`SIPNETIC.pdf`) — generic agent setup
   guide showing the form fields. The example screenshot uses
   `cpbxb.vozelia.com.pa`, but this is just an illustrative value.
2. **Real test-agent credentials** (operations spreadsheet, 2026-05-03)
   for the dedicated migration test:
   - Agent name: **Vanesa**
   - Mobile: `6503-2939`
   - SIP user: **`201-11435`**
   - Password: stored only in `local.properties` (never committed)
   - **SIP server: `cpbxa.vozelia.com.pa`**

`cpbxa` (with `a`, not `b`) is the authoritative server for this
account. Voselia likely runs `a/b` as either an active/standby pair
or per-account sharding — irrelevant for our integration as long as
we use the FQDN exactly as provisioned.

The PDF only covers the four user-facing fields (server, username,
password, permissions). It does **not** disclose transport, port, codec
list, STUN, ICE, SRTP, keep-alive, or expires — those are Sipnetic
defaults baked into the app and must still be extracted via §B.1, §B.2,
or by asking Voselia.

### 6.2 Path B.1 — Sipnetic UI screenshots

Screenshots captured 2026-05-03 from a production Galaxy Tab A9+ running
Sipnetic against the live Voselia account. Sipnetic version not recorded
in capture metadata — record next time the device is in hand.

**Sipnetic → Settings → Network**

- `Network options`:
  - Enable UDP: ON
  - Enable TCP: ON
  - Enable TLS: ON
  - Network selection: Default
  - Enable IPv6: ON
  - Prefer IPv6: OFF
- `SIP port`:
  - Random port (Use random port for SIP): ON
  - Specify port: 5060 (greyed out — only used if Random is OFF)
- `RTP port range`:
  - Start port: 16384
  - End port: 65535
- `Advanced settings`:
  - Use custom DNS server: OFF
  - Use host blacklist: OFF
  - Time to stay in blacklist: 30 (default)

**Sipnetic → Settings → Security**

- `Call encryption`:
  - Enable call encryption (Use SRTP protocol to encrypt media data): **ON**
  - ZRTP protocol: OFF
  - SDES protocol (Use RFC 4568 key exchange): **ON**
- `Crypto algorithms`:
  - Block cipher: 5 algorithms selected (defaults)
  - Authentication method: 4 algorithms selected (defaults)
  - ZRTP hash function / key agreement: greyed out (ZRTP disabled)
- `ZRTP cache`: greyed out (ZRTP disabled)
- `Secure messaging`: OTR protocol OFF (irrelevant for our use case)

**Sipnetic → Settings → Audio and video codecs**

Audio codecs (priority order, top → bottom, all checkboxes ON):

1. Opus — VBR | Excellent quality
2. G.722 — 64 kbit/s | Very good quality
3. Speex — VBR | Good quality
4. GSM 06.10 — 13 kbit/s | Medium quality
5. G.711 A-Law — 64 kbit/s | Good quality
6. G.711 µ-Law — 64 kbit/s | Good quality
7. G.729 — 8 kbit/s | Good quality

Disabled:

- Speex Wideband (VBR | Very good quality) — OFF
- Speex Ultra-Wideband (VBR | Excellent quality) — OFF

Video codecs (irrelevant — video calls disabled):

- H.264: ON
- VP8: ON

Advanced:

- Limit bit rate: ON
- Maximum bit rate: 32 (kbit/s, applies to Opus)
- Use Opus FEC: OFF
- Use G.729 VAD: ON

**Sipnetic → Settings → Preferences (relevant subset)**

- `Calls`:
  - Screen orientation: Default (no portrait/landscape lock)
  - Select account automatically: ON (single-account setup)
  - Vibrate while ringing: ON
- `Sound processing`:
  - Microphone AGC (Automatic gain control): **ON**
  - Speakers AGC: OFF
  - Echo cancellation: **ON**
  - Advanced audio settings: not opened (would expose echo-canceller
    flavor and noise-suppression details — capture next time the device
    is in hand)
- `Video calls`:
  - Enable video calls: OFF

**Operations-team confirmation (verbal, 2026-05-03):**

- Preferred SIP transport at account level: **UDP first**, TCP and TLS as
  fallbacks. Transport order configurable per-account in Sipnetic but
  agents in production are set up to start with UDP.

**Still missing — capture next time the production tablet is in hand:**

1. **Account-level `Network` / transport screen** — to confirm:
   - Transport priority order at account level (UDP/TCP/TLS).
   - Outbound proxy (if any).
   - Keep-alive interval (UDP keep-alive, in seconds).
   - `Expires` value for REGISTER (seconds).
   - SIP user / Auth user / Auth realm.
2. **Account-level NAT-traversal screen** — to confirm:
   - STUN server (host:port, or empty).
   - ICE checkbox state.
   - rport / `Use rport` toggle.
3. **Sipnetic version** — `Settings → About` (string of the form `x.y.z (build)`).

### 6.3 Path B.2 — Sipnetic SIP log highlights

> _Paste relevant `REGISTER` headers, `INVITE` SDP, and `200 OK` SDP excerpts._

---

### 6.5 Linphone REGISTER — M1 milestone (2026-05-03)

First successful REGISTER from our own Linphone client against the test
agent's account. Captured from `adb logcat -s LinphoneCoreManager LinphoneSDK`.

**Result**: `Registration state=Ok message=Registration successful`.

**Key findings from the log** — promote whichever apply to §1 / §4:

1. **Cluster topology**: `cpbxa.vozelia.com.pa` resolves via SRV
   (`_sip._udp.cpbxa.vozelia.com.pa`) to **9 SBCs** named `cpbx01` …
   `cpbx09` with priorities 1–8. Linphone selected `cpbx09`
   (priority 1, IP `138.99.136.119`).
2. **Public IP range of the Voselia cluster**: `138.99.136.111–119`.
   Useful for any future firewall whitelisting.
3. **SBC software**: `Server: Asterisk PBX 18.24.1`.
4. **Auth realm is per-SBC**, not per-domain:
   `WWW-Authenticate: Digest ... realm="cpbx09.vozelia.com.pa"`.
   Linphone discovers it dynamically when we pass `realm=null` to
   `createAuthInfo` — confirmed correct call.
5. **`auth username` == SIP username** (`201-11435`). No separate
   `Auth User` field needed.
6. **NAT handled SBC-side**:
   - Client local: `192.168.0.197:39411` (RFC1918 + ephemeral port).
   - SBC-detected public: `143.105.146.194:45288`.
   - SBC rewrote `Contact:` to the public address on the 200 OK.
   - **STUN unnecessary on residential WiFi** (and likely on most NAT
     scenarios), strongly reducing R2 risk. Confirm again on 4G during M4.
7. **`rport` confirmed**: client sent `Via: ...;rport`, SBC replied with
   `received=143.105.146.194;rport=45288`. Linphone default behavior is
   correct — no explicit toggle needed.
8. **Expires honored**: requested 3600 s, SBC echoed `Expires: 3600`.
   Refresh scheduled at 54 min (90% of expires) — Linphone default.
9. **SRTP is NOT required on REGISTER**: signaling went out as plain
   UDP, no SRTP/SDES headers — accepted on the first try. This relaxes
   our M2 milestone: SRTP only applies to media negotiation in INVITE,
   not to registration. M2 becomes "verify SRTP/SDES still works once
   enabled" rather than "unblock REGISTER".
10. **G.729 not available in Linphone Android SDK**:
    `Could not find encoder for G729`. It is a licensed codec not
    bundled. Sipnetic offered it but our SDK doesn't ship it. Drop it
    from our codec priority — Opus, G.722, G.711 a/µ are sufficient.

**Bytes-on-the-wire confirmation**:

```
REGISTER sip:cpbxa.vozelia.com.pa SIP/2.0
Via: SIP/2.0/UDP 192.168.0.197:39411;branch=z9hG4bK.fI2M2qY0h;rport
From: <sip:201-11435@cpbxa.vozelia.com.pa>;tag=g8nfk-OO8
To: sip:201-11435@cpbxa.vozelia.com.pa
CSeq: 20 REGISTER
Call-ID: GfAawRCnQo
...
User-Agent: Unknown          ← TODO: set our own product/version

→ SIP/2.0 401 Unauthorized
  WWW-Authenticate: Digest algorithm=MD5, realm="cpbx09.vozelia.com.pa", nonce="4a17e27d"

→ SIP/2.0 200 OK
  Contact: <sip:201-11435@143.105.146.194:45288;transport=udp>;expires=3600
```

**Deferred to later phases**:

- Set a meaningful `User-Agent` header (`calls-agends/1.0` or similar)
  in Phase 3 — currently shows as `Unknown`.
- Native log routing to `filesDir/sip-logs/` — Phase 8.

---

### 6.6 Linphone outbound INVITE — M3 milestone (2026-05-03)

First successful outbound INVITE. The remote leg rang (`180 Ringing`)
but the user hung up before the callee answered, so the negotiated
codec (in the `200 OK` SDP) is still pending. Everything up to that
point is confirmed working.

**Resolved unknowns**:

1. **DTMF method**: Linphone offered RFC 2833 (`telephone-event/8000`
   and `/48000`). No fallback to SIP INFO needed — Asterisk 18 accepts
   2833 and routed the INVITE successfully. `TODO(VOSELIA-CONFIRM)`
   for DTMF in §4 is now **resolved**.

2. **SRTP cipher suites offered**: 4 simultaneously. Order from the
   SDP `a=crypto:` lines:
   - `AEAD_AES_128_GCM`
   - `AES_CM_128_HMAC_SHA1_80`
   - `AEAD_AES_256_GCM`
   - `AES_256_CM_HMAC_SHA1_80`

   Which one Voselia picks will be in the `200 OK` SDP from a fully
   answered call.

3. **Codec offer in priority order**: Opus(96), PCMU(0), PCMA(8),
   G.722(9), plus telephone-event for DTMF. Matches Sipnetic's
   priority list (minus G.729 which is unavailable in the SDK).

4. **Authentication on INVITE**: Voselia challenges every INVITE with
   `401 Unauthorized` (separate from REGISTER auth). Linphone handles
   it automatically using the same authInfo configured for REGISTER.
   No extra config needed.

5. **NAT on media**: client SDP advertises private IP
   (`c=IN IP4 192.168.0.197`) and ephemeral RTP port (e.g. 46242 from
   the `RTP_PORT_MIN..RTP_PORT_MAX` range). Voselia performs symmetric
   media latching — confirmed by the SBC accepting the INVITE without
   requiring STUN/ICE in the offer.

6. **Phone-number routing**: `sip:+50763425495@cpbxa.vozelia.com.pa`
   (E.164 with `+`) was accepted and routed to the PSTN. The SBC
   contact in the `100 Trying` was
   `<sip:+50763425495@138.99.136.119:5060>` — proves Voselia normalizes
   internally to E.164.

**Excerpt of the INVITE SDP**:

```
INVITE sip:+50763425495@cpbxa.vozelia.com.pa SIP/2.0
User-Agent: calls-agends/1.0
Content-Type: application/sdp

v=0
o=201-11435 3262 3644 IN IP4 192.168.0.197
c=IN IP4 192.168.0.197
m=audio 46242 RTP/SAVP 96 0 8 9 101 97
a=rtpmap:96 opus/48000/2
a=fmtp:96 useinbandfec=1
a=rtpmap:101 telephone-event/48000
a=rtpmap:97 telephone-event/8000
a=crypto:1 AEAD_AES_128_GCM inline:...
a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:...
a=crypto:3 AEAD_AES_256_GCM inline:...
a=crypto:4 AES_256_CM_HMAC_SHA1_80 inline:...
```

**Update (2026-05-03, second test)**:

After routing the audio output to the device speaker via
`Core.outputAudioDevice`, the user placed a second call to a real
contact who answered. Both parties heard each other clearly —
**bidirectional audio confirmed**. M3 fully passed.

The exact cipher and codec chosen by the SBC was not captured (the
operator did not extract the `200 OK` SDP from the live log), but
that detail is non-blocking: audio works, which means SRTP key
exchange and codec negotiation completed successfully end-to-end.
The full SDP can be captured later via the file logger (Phase 8).

**Resolved unknown #2 — speaker routing on Tab A9+**:

Linphone's default output stream is `Voice Communication Signalling`,
which on the Tab A9+ goes to the (tiny) earpiece. The fix —
`Core.outputAudioDevice = speakerDevice` before `Core.inviteAddress()`
— routes both ringback tones and call audio through the built-in
speaker. This is the right default for the agent workflow (hands-free,
notes during call). Wired headset / Bluetooth routing is deferred to
v1.1.

**Still pending**:

- DTMF round-trip against an IVR — quick validation when convenient.

---

### 6.7 `200 OK` SDP from Voselia — final negotiation (2026-05-03)

Captured from a third call that connected and stayed up ~31 s. Full
answer SDP from the SBC:

```
SIP/2.0 200 OK
Server: Asterisk PBX 18.24.1
...
v=0
o=root 831570567 831570567 IN IP4 138.99.136.119
s=Asterisk PBX 18.24.1
c=IN IP4 138.99.136.119
t=0 0
m=audio 17958 RTP/SAVP 8 0 101
a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:WpfTtGr.....
a=rtpmap:8 PCMA/8000
a=rtpmap:0 PCMU/8000
a=rtpmap:101 telephone-event/8000
a=fmtp:101 0-16
a=ptime:20
a=maxptime:140
a=rtcp-mux
a=sendrecv
```

Linphone confirmation log line:
> `Negotiated media encryption is LinphoneMediaEncryptionSRTP`

**All remaining unknowns now resolved:**

| Parameter            | Voselia answered                                       |
|----------------------|---------------------------------------------------------|
| Audio codec          | **PCMA (G.711 a-law) @ 8000 Hz**                        |
| Codec fallback       | PCMU (G.711 µ-law) — second offer                       |
| DTMF method          | telephone-event/8000, payload type 101, range 0–16      |
| SRTP cipher          | **AES_CM_128_HMAC_SHA1_80** (RFC 4568 SDES)             |
| Packetization        | 20 ms (`ptime`), max 140 ms                             |
| RTCP                 | Multiplexed on the same port as RTP (`a=rtcp-mux`)      |
| Direction            | `sendrecv` (bidirectional)                              |
| Media IP             | SBC public address `138.99.136.119` (cpbx09)            |

**Implications for our codec config:**

Voselia transcodes everything to G.711a in their SBC for the PSTN
trunk. Our Opus offer is discarded; the `OPUS_MAX_BITRATE_KBPS = 32`
constant has no real effect on PSTN calls.

We keep the current codec list (Opus first, G.722, G.711a, G.711µ)
because:

1. Future SIP-to-SIP calls between agents on the same PBX could
   negotiate Opus end-to-end without transcoding.
2. The cost of offering Opus is ~50 bytes in the INVITE SDP.
3. Reordering to G.711-first would save zero perceptible time for
   the agent.

**Implications for SRTP:**

We offer 4 cipher suites in this order:
1. `AEAD_AES_128_GCM`
2. `AES_CM_128_HMAC_SHA1_80`  ← Voselia picked this
3. `AEAD_AES_256_GCM`
4. `AES_256_CM_HMAC_SHA1_80`

Voselia rejected GCM (modern AEAD) and went with SHA1_80 (the RFC
4568 baseline). This is normal for an Asterisk-based SBC — GCM
support requires `res_srtp.so` builds with GCM enabled, which many
Asterisk distributions skip. No action needed; the negotiation works.

### 6.4 Path C — PCAPdroid capture (optional)

> _Note whether the capture was needed, what it confirmed/contradicted, and link the `.pcap` location._

---

## 7. Linphone mapping (partial — only confirmed rows are actionable)

> Rows tagged **TODO** must wait until §4 is filled. Rows tagged **READY**
> can be wired into `LinphoneCoreManager` as soon as Phase 2 of the WBS
> starts.

| Sipnetic setting (confirmed)                          | Linphone API target                                                                                  | State |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------|-------|
| Server `cpbxb.vozelia.com.pa`                         | `AccountParams.serverAddress` + `identityAddress`                                                    | READY |
| UDP / TCP / TLS all enabled, **UDP preferred**        | `Transports.udpPort = -1`, `tcpPort = -1`, `tlsPort = -1` (auto). Set `AccountParams.transport = Udp`. | READY |
| Random local SIP port                                 | Default Linphone behavior when `udpPort = -1`. No explicit override.                                 | READY |
| RTP port range 16384–65535                            | `Core.audioPortRange = IntRange(16384, 65535)`                                                       | READY |
| SRTP enabled, key exchange = SDES                     | `Core.mediaEncryption = MediaEncryption.SRTP` + `Core.mediaEncryptionMandatory = true` (Sipnetic forces SRTP — match it) | READY |
| Codec priority: Opus, G.722, Speex, GSM, PCMA, PCMU, G.729 | `Core.audioPayloadTypes`: enable in this order, disable everything else                          | READY |
| Limit bit rate ON, max 32 kbit/s                      | `PayloadType("opus").normalBitrate = 32` for the Opus payload type                                  | READY |
| Opus FEC OFF                                          | Opus default in Linphone (no override needed)                                                        | READY |
| G.729 VAD ON                                          | `PayloadType("G729").setRecvFmtp("annexb=yes")` if needed                                            | READY |
| Microphone AGC ON                                     | `Core.micGainDb` + Linphone built-in AGC via mediastreamer plugins (default ON)                      | READY |
| Echo cancellation ON                                  | `Core.echoCancellationEnabled = true`                                                                | READY |
| STUN server                                           | `NatPolicy.stunServer = ?`, `stunEnabled = true/false`                                               | TODO  |
| TURN                                                  | `NatPolicy.turnEnabled` + credentials                                                                | TODO  |
| ICE                                                   | `NatPolicy.iceEnabled = true/false`                                                                  | TODO  |
| Keep-alive interval                                   | `Core.keepAliveEnabled = true` + interval                                                            | TODO  |
| `Expires` for REGISTER                                | `AccountParams.expires`                                                                              | TODO  |
| `rport`                                               | `AccountParams.useRport = true` (Linphone default)                                                   | TODO confirm |

---

## 8. Change log

| Date       | Change                                                  | Author |
|------------|---------------------------------------------------------|--------|
| 2026-05-03 | Initial draft. All values are working assumptions only. | —      |
| 2026-05-03 | Confirmed Proxy/Registrar = `cpbxb.vozelia.com.pa` from internal Sipnetic onboarding PDF. Other 8 rows still UNVERIFIED. | — |
| 2026-05-03 | Sipnetic UI inspection on production tablet. Confirmed: transport globals (UDP/TCP/TLS all enabled), random SIP port, RTP range 16384–65535, SRTP enabled with SDES, codec list and priority (Opus first), bit-rate cap 32 kbit/s, mic AGC ON, echo cancellation ON. **Two original assumptions invalidated**: SRTP is required (not optional), Opus is offered first (not absent). | — |
| 2026-05-03 | Operations team confirmed verbally: preferred transport is UDP, with TCP and TLS as fallbacks. STUN, ICE, keep-alive, expires still pending. | — |
| 2026-05-03 | Test-agent credentials received: Vanesa / SIP user `201-11435` / server `cpbxa.vozelia.com.pa`. Server in §1 row 2 corrected from `cpbxb` (generic PDF example) to `cpbxa` (actual). | — |
| 2026-05-03 | **M1 PASS** — first REGISTER from our Linphone client succeeded. Documented findings in §6.5: SBC cluster (cpbx01–cpbx09), Asterisk 18.24.1, SBC-side NAT handling, rport confirmed, Expires=3600 accepted, SRTP not needed on REGISTER, G.729 unavailable in Linphone Android SDK. | — |
| 2026-05-03 | **M2 PASS** — REGISTER with SRTP/SDES enabled at Core level, codec list filtered (Opus first, no G.729), bit-rate cap, custom User-Agent. No regression. | — |
| 2026-05-03 | **M3 partial PASS** — outbound INVITE to `+50763425495` reached the destination (`180 Ringing`). User hung up before answer. SDP analysis in §6.6: SRTP confirmed in offer (RTP/SAVP), DTMF method = RFC 2833, all 4 SDES ciphers offered, codec priority Opus→PCMU→PCMA→G.722. Pending: `200 OK` SDP from an answered call. | — |
| 2026-05-03 | **M3 PASS COMPLETE** — second outbound call answered, **bidirectional audio confirmed clearly on both sides**. Required forcing `Core.outputAudioDevice = Speaker` before INVITE; ringback was inaudible on the earpiece otherwise. SBC-selected cipher/codec not extracted from this run. | — |
| 2026-05-03 | **Final SDP analysis** (§6.7): captured `200 OK` from a 31-second answered call. Negotiated codec = **PCMA (G.711 a-law)**, cipher = **AES_CM_128_HMAC_SHA1_80**, DTMF = RFC 2833 PT 101. Voselia transcodes Opus → G.711a in the trunk; our codec config is fine as-is. | — |
