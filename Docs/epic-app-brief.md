# HealthAggregator — App Brief for Epic Customer Review

> Provide this to any Epic customer (Cleveland Clinic, Summa Health, etc.) who requests app documentation as part of their internal vetting process before downloading the production client_id into their Epic environment.

## Summary

HealthAggregator is a **personal-use patient-directed Personal Health Record (PHR) aggregator**. It runs entirely on the patient's own computer and stores data in a local SQLite database on that device. There is no cloud backend, no third-party hosting, and no data-sharing pipeline.

## Ownership & contact

- **Developer / maintainer:** Jesse Coble
- **Email:** coble.jesse@gmail.com
- **App type:** Non-commercial, single-user, operated by the patient on the patient's own hardware
- **Organization:** None (personal / individual developer)

## Architecture

| Component | Technology | Where it runs |
|---|---|---|
| Backend API | .NET 10 Web API (C#) | Localhost on patient's Mac |
| Frontend | SvelteKit 2 / Svelte 5 / Tailwind 4 | Localhost on patient's Mac |
| Storage | SQLite via EF Core | Single `.db` file in `~/Library/Application Support/HealthAggregator/` on patient's Mac |
| Authentication | SMART on FHIR OAuth 2.0 + PKCE (S256) | Patient authenticates via MyChart against the Epic customer's own authorization server |

**No server-side components run anywhere other than the patient's own device.** The app does not send data to any external service. All FHIR traffic is Mac-to-Epic directly.

## SMART on FHIR profile

- **Client type:** Public client (PKCE)
- **Client secret:** Not used (public client; PKCE S256 replaces it)
- **Redirect URI(s):** Registered on fhir.epic.com as `https://localhost:<port>/api/integrations/epic/callback`. This is a loopback redirect, per the Epic on FHIR documentation allowance for native/personal applications.
- **Token storage:** Access tokens and refresh tokens are stored in the local SQLite database on the patient's own machine. They never leave the device.
- **Token lifetime:** Standard Epic lifetimes (access token ~1 hour; refresh token ~3 months) are honored. Refresh flow used.

## FHIR scopes requested

Read-only scopes only. No write scopes. No administrative scopes.

```
launch/patient
patient/Patient.read
patient/Observation.read
patient/DiagnosticReport.read
patient/Condition.read
patient/MedicationRequest.read
patient/AllergyIntolerance.read
patient/Encounter.read
patient/DocumentReference.read
patient/Binary.read
offline_access
```

The `offline_access` scope is requested so the patient doesn't have to re-authenticate every hour during a session. Refresh tokens are used only to extend the single logged-in patient session, never to perform background access outside the patient's explicit intent.

## FHIR resources accessed

Every FHIR resource the app reads is listed below, along with what the app does with it. All resources are stored as raw JSON in a `SourceRecord` table plus extracted into typed tables for query:

| Resource | Why the app reads it |
|---|---|
| `Patient` | Identify the authenticated patient, store demographics |
| `Observation` (category `laboratory`) | Lab results, with LOINC code, value, unit, reference range, interpretation |
| `Observation` (category `vital-signs`) | Vitals (BP, HR, weight, etc.) — stored raw, structured parsing in a future release |
| `DiagnosticReport` | Panel-level diagnostic reports linking multiple observations |
| `Condition` | Chronic and active conditions (problem list) |
| `MedicationRequest`, `MedicationStatement` | Active and historical medications |
| `AllergyIntolerance` | Allergies and intolerances |
| `Encounter` | Visit history |
| `DocumentReference` | Clinical notes, visit summaries, discharge summaries (metadata; binaries fetched on demand) |

## Data flow (diagram in words)

```
Epic MyChart (Cleveland Clinic / Summa Health production server)
        |
        |  1. Patient authenticates via MyChart
        |  2. OAuth authorize → redirect to localhost with code
        |  3. PKCE code exchange → access + refresh tokens (stored locally)
        |  4. FHIR GET for each scoped resource
        v
HealthAggregator on patient's Mac (localhost only)
        |
        v
Local SQLite file on patient's Mac
        (~/Library/Application Support/HealthAggregator/*.db)
```

**No data touches any server other than Epic's and the patient's own device.**

## Privacy & security posture

- **HIPAA covered-entity status:** The app operates under the patient's own use of their own health information. The patient is not a covered entity; this is the patient's personal use of their own PHI. No BAA is required for this relationship because no covered entity is disclosing PHI to a business associate — Epic is disclosing PHI to the patient, who is using an app on their own device.
- **Encryption at rest:** The SQLite database inherits macOS FileVault full-disk encryption. The file is stored in `~/Library/Application Support/`, which is protected by the user's login password.
- **Encryption in transit:** All traffic to Epic FHIR endpoints is HTTPS/TLS 1.2+. The app uses the system TLS stack and validates certificates normally.
- **Secret management:** The Epic client_id is stored via `dotnet user-secrets` (not in source control, not in the database). Access tokens are stored in the local SQLite.
- **No logging of PHI:** Application logs are written to `/tmp/healthaggregator-api.log` during development and do not include PHI. Production logging omits token values and narrative text.
- **No third-party services:** The app makes no outbound HTTP calls except to Epic FHIR endpoints and the Epic `.well-known/smart-configuration` discovery endpoint. No analytics, no crash reporting, no cloud backup.
- **Source availability:** The full source is in a private git repository maintained by the developer. Can be shared with Epic customer review teams on request.

## Revocation / shutdown

If the customer wishes to revoke access:

- The patient can disconnect in-app via the `/connections` page — this removes the `EpicConnection` row and clears tokens, but retains previously-downloaded records locally.
- The customer can revoke the refresh token via Epic's standard token-revocation mechanism; subsequent FHIR calls will fail with 401.
- The customer can remove the client_id download from their Epic environment at any time — all future authentications for this app against that customer will fail.

## Data retention and deletion

- All records are retained in the local SQLite file indefinitely, under the patient's control.
- The patient can delete the SQLite file at any time, destroying all locally-stored records.
- The app does not back up the database to any external service. If the patient's Mac dies, the records are gone (unless the patient has set up their own backup, e.g., Time Machine).

## Contact for customer questions

If the customer's review team has specific questions not answered above, contact the developer at **coble.jesse@gmail.com**.

Happy to provide additional attestations in any format required (security questionnaire, privacy policy, SOC 2 scope — noting that SOC 2 is not applicable to a single-user self-hosted app). Happy to jump on a call.
