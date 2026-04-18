# Epic App Approval Playbook — Cleveland Clinic + Summa Health

> Date: 2026-04-18 · For: Jesse Coble · Re: registering HealthAggregator production client_id at both orgs

## Sequence

1. **Graduate the app** at `https://fhir.epic.com/` — Mark Ready for Sandbox (if not already) → Mark Ready for Production. App record becomes immutable once production-marked. Production client_id becomes usable immediately.
2. **(Strongly recommended) Get the app listed on Epic Showroom** at `https://open.epic.com/Contact` — this raises outreach credibility from "random patient" to "verified Epic-listed app". Email them requesting a Showroom listing for a patient-facing standalone SMART app. This listing step is optional per Epic docs but in practice is the difference between "we'll evaluate it" and "we don't work with unknown apps".
3. **Copy the production client_id** into `Epic:ProductionClientId`:
   ```bash
   dotnet user-secrets set "Epic:ProductionClientId" "<prod-id>" --project HealthAggregator.Api
   ```
4. **Send the emails below.** Expect weeks-to-months, not days, for response.
5. **If ignored or refused** beyond 30 days: file an information-blocking complaint at `https://healthit.gov/feedback` citing the HIPAA right-of-access violation. Keep every email + call log. Fasten Health's `github.com/fastenhealth/information-blocking-complaints` repo has a precedent database.

## App facts (for both emails)

| Field | Value |
|---|---|
| App name | HealthAggregator (personal, single-user) |
| App type | Patient-facing standalone SMART on FHIR |
| OAuth profile | Public client + PKCE (S256) |
| Production Client ID | **[FILL AFTER GRADUATION]** |
| Non-Production Client ID | `a8b55f20-a2d5-477e-9dba-b12e5592e4c4` (sandbox; included only if they ask) |
| Data residency | Local SQLite on the patient's own Mac; no cloud storage, no third-party sharing, no aggregation service |
| Redirect URI | `[YOUR PRODUCTION CALLBACK URL]` — currently `https://localhost:5010/api/integrations/epic/callback` during dev; set to the production URL registered on fhir.epic.com |
| FHIR resources requested | `Patient`, `Observation`, `DiagnosticReport`, `Condition`, `MedicationRequest`, `AllergyIntolerance`, `Encounter`, `DocumentReference`, `Binary` |
| Scopes requested | `launch/patient`, `patient/Patient.read`, `patient/Observation.read`, `patient/DiagnosticReport.read`, `patient/Condition.read`, `patient/MedicationRequest.read`, `patient/AllergyIntolerance.read`, `patient/Encounter.read`, `patient/DocumentReference.read`, `patient/Binary.read`, `offline_access` |
| Intended use | Personal diagnostic research. Aggregate my own longitudinal health records across Cleveland Clinic + Summa Health to surface trends across providers for unresolved health conditions. |

**Do not include your MRN in the initial email.** They will ask if they need it to verify you are an established patient. Volunteering MRN invites it to bounce around internal routing before you know who owns the request.

---

## Email 1 — Cleveland Clinic

**To:** `privacy@ccf.org`
**CC:** `MyPracticeComm@ccf.org` *(secondary — they're not the target team but they sit inside the Cleveland Clinic Epic org and can internally route)*
**Subject:** HIPAA Right-of-Access Request — Register Patient-Directed Third-Party App for Epic FHIR API

Hello,

I am a current Cleveland Clinic patient writing to submit a **HIPAA right-of-access request** under 45 CFR 164.524(c)(3)(ii) to direct my own electronic health information to a third-party application of my choice. Specifically, I am requesting that Cleveland Clinic **download my app's Epic client record** into your Epic production environment so I can authenticate to Cleveland Clinic's patient-facing FHIR R4 API (`https://api.ccf.org/mu/api/FHIR/R4`) with the app.

### The app

- **Name:** HealthAggregator
- **Type:** Patient-facing standalone SMART on FHIR application (public client + PKCE)
- **Purpose:** Aggregating *my own* longitudinal health records — labs, medications, conditions, encounters, documents — across my Cleveland Clinic and Summa Health records, so I can reason over cross-provider trends for long-standing health concerns that have not been resolved by individual-provider review.
- **Deployment:** Runs locally on my personal computer. All data stays in a SQLite file on my machine. No cloud hosting. No third-party aggregator. No data sharing.
- **Production Client ID:** `[PRODUCTION_CLIENT_ID]`
- **Registration home:** `https://fhir.epic.com` (Ready for Production).

### FHIR scopes and resources requested

The app requests read-only access to the following resources on my behalf:

```
patient/Patient.read
patient/Observation.read
patient/DiagnosticReport.read
patient/Condition.read
patient/MedicationRequest.read
patient/AllergyIntolerance.read
patient/Encounter.read
patient/DocumentReference.read
patient/Binary.read
launch/patient
offline_access
```

No write scopes are requested. The app cannot and will not modify any record in your system.

### What I am specifically asking for

1. **Download the Epic client record** for client_id `[PRODUCTION_CLIENT_ID]` into Cleveland Clinic's Epic **production** environment, per the process at `https://fhir.epic.com/Documentation?docId=appapproval`.
2. Let me know once the download is complete so I can initiate the OAuth flow via MyChart and authorize the app against my own account.
3. If you require additional information (a privacy policy, security attestation, MRN verification, etc.), please let me know what you need and I will provide it.

### Regulatory framing

This request is made under the patient's HIPAA right to direct protected health information to a third party of the patient's choosing (45 CFR 164.524(c)(3)(ii)). The ONC 21st Century Cures Act information-blocking rule (42 U.S.C. 300jj-52) further prohibits covered actors from implementing practices that interfere with patient-directed access to a certified API. Cleveland Clinic's Epic implementation is subject to both.

I understand that the process is gated on Cleveland Clinic's internal download step — I am not asking you to waive that review, I am requesting that it be scheduled.

Happy to jump on a call if that's faster. Thank you for your time.

Best,
Jesse Coble
`coble.jesse@gmail.com`
*(MRN available on request for patient verification)*

---

## Email 2 — Summa Health

**To:** `summamychartsupport@summahealth.org`
**Subject:** HIPAA Right-of-Access Request — Patient-Directed App Registration for Epic FHIR API

Hello,

I am a current Summa Health patient writing to submit a **HIPAA right-of-access request** under 45 CFR 164.524(c)(3)(ii). I am requesting that Summa Health **download my app's Epic client record** into your Epic production environment so I can authenticate to your patient-facing FHIR R4 API (`https://epicproxy.et1289.epichosted.com/FHIRProxy/api/FHIR/R4`) with the app.

I recognize that MyChart Support is likely not the team that executes Epic app downloads internally. **Could you please route this request to whoever administers the Epic environment** — clinical informatics, IT integration, or the Epic administrator — and let me know who owns it so I can follow up directly?

### The app

- **Name:** HealthAggregator
- **Type:** Patient-facing standalone SMART on FHIR application (public client + PKCE)
- **Purpose:** Aggregating *my own* longitudinal health records from Summa Health and Cleveland Clinic into a single local database to help me reason about cross-provider trends for long-standing health conditions.
- **Deployment:** Runs locally on my personal computer. All data stays on my machine in an SQLite database. No cloud hosting. No data sharing with third parties.
- **Production Client ID:** `[PRODUCTION_CLIENT_ID]`
- **Registration home:** `https://fhir.epic.com` (Ready for Production).

### FHIR scopes

The app requests the following read-only scopes on my behalf:

```
patient/Patient.read
patient/Observation.read
patient/DiagnosticReport.read
patient/Condition.read
patient/MedicationRequest.read
patient/AllergyIntolerance.read
patient/Encounter.read
patient/DocumentReference.read
patient/Binary.read
launch/patient
offline_access
```

No write scopes are requested.

### What I am specifically asking for

1. **Route this request** to whoever manages Epic app downloads at Summa Health.
2. **Download the Epic client record** for client_id `[PRODUCTION_CLIENT_ID]` into Summa's Epic production environment, per `https://fhir.epic.com/Documentation?docId=appapproval`.
3. Confirm when the download is complete so I can authorize the app against my own MyChart account.
4. If additional documentation is needed (privacy policy, security attestation, MRN verification), let me know and I will provide it.

### Regulatory framing

This is a patient-directed access request under 45 CFR 164.524(c)(3)(ii) (HIPAA) and 42 U.S.C. 300jj-52 (information-blocking rule of the 21st Century Cures Act). Both obligate Summa Health to engage with patient-directed third-party app requests rather than referring patients back to the MyChart portal exclusively.

Thank you for routing this. Happy to call if that's faster — please include a phone extension when you route.

Best,
Jesse Coble
`coble.jesse@gmail.com`
*(MRN available on request for patient verification)*

---

## Phone call script (backup)

If email goes nowhere for 7-10 days, call. Script:

> "Hi, my name is Jesse Coble, I'm a patient here. I'm submitting a **HIPAA right-of-access request** to have an Epic app I've built downloaded into your Epic environment so I can access my own records through it. I know this isn't a standard MyChart ticket — I'm looking for whoever at your organization administers the Epic environment or handles third-party patient-app approvals. Could you route me to that team or give me their contact info? I'll also email the formal request in writing."

**Numbers:**
- Cleveland Clinic: `216.445.9782` (EHI Export / HIM) → escalate to Clinical Informatics. General: `800.223.2273`.
- Summa Health: `234.475.6789` (MyChart 24/7) → ask for Epic administrator / IT integration. General: `330.375.3000` or `1.800.237.8662`.

## If refused or ignored beyond 30 days

File an information-blocking complaint at `https://healthit.gov/feedback`. Structure:

- **Alleged actor:** Cleveland Clinic Foundation / Summa Health (as applicable)
- **Type of practice:** Interference with patient-directed access to certified API
- **Factual summary:** dates of request, channels used, responses (or absence), regulatory citations
- **Evidence:** forward every email thread + call notes

Cite `fastenhealth/information-blocking-complaints/IB-2820-labcorp.md` as a precedent — same complaint shape has been successfully used against LabCorp.

## Notes

- **Summa Health is in organizational flux** (acquired by HATCo / General Catalyst in 2024, went for-profit). IT ownership may be changing. Your contact there may bounce; keep a paper trail and re-route as needed.
- Cleveland Clinic's MyChart Interoperability Guide at `https://mychart.clevelandclinic.org/public/interoperabilityGuide.html` mentions a "questionnaire" for app approval but doesn't publish it. If they send it to you, save a copy — it will be useful if this pattern needs to repeat with any other org in the future.
- Save all correspondence. Bcc yourself on every outbound email.
