# EUDI reference wallet, Plenipo test build

This fork of [eudi-app-android-wallet-ui](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui)
adds what is needed to approve a [Plenipo](https://plenipo.net) mandate: a mandate for an AI agent,
carried as OpenID4VP transaction data of type `plenipo_mandate`, which the person reads and approves
in the wallet and which a bank then verifies. It is a test build for the Plenipo sandbox, not a product,
and it is not endorsed by the European Commission. Licence: EUPL-1.2, as upstream.

## What changes

- `plenipo_mandate` is declared as a transaction data type (`TransactionDataType.raw`), next to the QES
  types, in the dev and demo wallet configurations.
- The consent screen shows every field of the mandate that limits the agent: purpose, actions,
  counterparties (or "any"), limits per operation and in total, validity, delegation, the legal person
  when there is one, and the mandate id. The bank checks the hash of exactly these bytes. A mandate with
  a member this build does not know is not shown in part: the request is declined, as upstream does for
  transaction data it cannot display.
- Relying parties are authenticated against the CA of the Plenipo sandbox platform
  (`resources-logic/src/main/res/raw/plenipo_sandbox_rp_ca.pem`, published at
  https://platform.sandbox.plenipo.net/rp-ca.pem) instead of the EU list of relying party access
  certificate providers, which the sandbox is not on. Other verifiers are therefore not trusted by this
  build.
- Application id `net.plenipo.eudiwallet` and name "EUDI Wallet (Plenipo test)", so it installs next to
  the EU app and is not mistaken for it.

## Build

GitHub Actions (`.github/workflows/plenipo-build.yml`) runs the unit tests and builds the dev and demo
debug APKs on every push; download them from the run's artifacts, or from a `plenipo-v*` release.
Locally: `./gradlew :app:assembleDevDebug` with JDK 17 and the Android SDK.

## Try it

These steps passed end to end on 15 November 2026 with `plenipo-v0.1.2` on the Android emulator
(plenipo-net/plenipo#44).

1. Install `app-dev-debug.apk` from the latest `plenipo-v*` release on an Android emulator or phone (drag
   it onto the emulator window). Uninstall an earlier build first from Settings › Apps: each CI build is
   signed with a different debug key.
2. Set the PIN, then Add document › under **issuer.eudiw.dev** › "PID Combined" › the country form ›
   Maria Rossi, 1984-03-12, IT (the sandbox accounts are Maria Rossi and Luca Bianchi, 1979-11-02).
   "PID Combined" under issuer-backend.eudiw.dev has not been tried end to end yet.
3. Ask for a mandate: in Claude or another MCP client with the Plenipo sandbox MCP server, or from
   https://sandbox.plenipo.net. Open `https://platform.sandbox.plenipo.net/requests/<id>` in the
   device's browser and tap "Open in a wallet on this device", or scan the QR code on that page.
4. Choose **"PID (SD-JWT VC)"**, read the mandate and approve it **within 10 minutes** of the request.
5. The agent's payments are then allowed or refused by the bank within the mandate.

Open points are tracked in https://github.com/plenipo-net/plenipo/issues/35.
