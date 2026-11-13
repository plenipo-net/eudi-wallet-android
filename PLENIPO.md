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

1. Install the APK on an Android emulator or phone and add a PID from the EU test issuer in SD-JWT VC
   format (`urn:eudi:pid:1`). The sandbox accounts are Maria Rossi (1984-03-12) and Luca Bianchi
   (1979-11-02): enter one of them as test identity data.
2. In Claude or another MCP client, connect the Plenipo sandbox MCP server and ask the agent to request
   a mandate; or open https://sandbox.plenipo.net and start a request from the platform.
3. Open the request link (`openid4vp://` or `eudi-openid4vp://authorize?...`) on the device, or scan the
   QR code on the request page, read the mandate and approve it.
4. The agent's payments are then allowed or refused by the bank within the mandate.

Open points are tracked in https://github.com/plenipo-net/plenipo/issues/35.
