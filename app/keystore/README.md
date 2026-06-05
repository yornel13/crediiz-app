# Release Signing — Disaster Recovery

This directory holds **the most important file in the project**:
`release.jks`. Everything else here is `.gitignore`d on purpose.

## Why this matters

We distribute `calls-agends` as a **sideloaded enterprise APK**, not
through the Play Store. Android enforces that an update may only be
installed if its APK is signed by the **same** keystore as the version
currently on the device. There is no recovery mechanism: no Google
support to email, no Play Console re-key option.

If `release.jks` (or its passwords) is ever lost:

- Every tablet currently running a signed build is **stuck on that
  version forever**.
- The only path to a new version is **factory-reset every tablet** and
  re-onboard from scratch, losing local Room state.

Treat this file with the same care as production database backups.

## What to back up

1. The file `app/keystore/release.jks` itself.
2. The four `RELEASE_*` properties stored in
   `<repo-root>/local.properties` (gitignored).

Both items belong in:

- The org password manager (1Password / Bitwarden / Vaultwarden), in a
  dedicated `vortex-callsagent-signing` vault item with restricted
  access.
- A second, offline copy (encrypted USB, sealed envelope, etc.) held by
  a different person than whoever runs builds.

## Current keystore facts

- Path: `app/keystore/release.jks`
- Format: PKCS12 (Java 9+ default; store and key passwords are unified)
- Alias: `callsagent-release`
- Algorithm: RSA 4096
- Validity: 100 years (≈ until 2126-04-29) — we never want this to
  expire during the product's lifetime
- DN: `CN=Vortex Calls Agent, OU=Mobile, O=Vortex, L=Panama City, ST=Panama, C=PA`

## Regenerating (only acceptable on a fresh fleet)

Only run this when **no tablet in the wild** is yet signed by a
previous keystore. After the first signed APK is installed on any
device, regenerating means bricking that device's update path.

```bash
keytool -genkeypair -v \
  -keystore app/keystore/release.jks \
  -alias callsagent-release \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -dname "CN=Vortex Calls Agent, OU=Mobile, O=Vortex, L=Panama City, ST=Panama, C=PA"
```

Then update the four `RELEASE_*` keys in `local.properties` and re-run
`./gradlew :app:assembleRelease` to confirm it picks them up.

## Inspecting the current keystore

```bash
keytool -list -v -keystore app/keystore/release.jks
# (paste RELEASE_STORE_PASSWORD when prompted)
```

The printed SHA-256 fingerprint is the value Android compares on
install. Record it in the password-manager entry so you can verify a
backup before trusting it.
