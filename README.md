# HealthAggregator

Personal health record prototype with Epic/MyChart as the first integration path, manual imports as fallback, local SQLite storage, and a read-only assistant.

## Stack

- Backend: .NET 10 Web API
- Frontend: SvelteKit 2 + Tailwind 4
- Database: SQLite through EF Core
- First live integration: Epic SMART on FHIR OAuth

## Run Locally

```bash
./scripts/start-dev.sh
```

Services:

- API: `https://localhost:5310`
- Web: `https://localhost:5373`
- Epic callback listener: `https://localhost:5010`

## Epic Sandbox Setup

The Epic sandbox endpoints are preconfigured. Set `Epic:ClientId` for the API before starting the SMART flow:

```bash
dotnet user-secrets set "Epic:ClientId" "<your-epic-client-id>" --project HealthAggregator.Api
```

The current local callback URL used for sandbox testing is:

```text
https://localhost:5010/api/integrations/epic/callback
```

After Epic has propagated the new isolated API port, clear `Epic:CallbackBaseUrl` or set `HEALTHAGGREGATOR_EPIC_CALLBACK_PORT=5310` to use:

```text
https://localhost:5310/api/integrations/epic/callback
```

## Current Scope

- Read-only SMART on FHIR connect/sync scaffolding
- Raw FHIR resource storage in SQLite
- Normalized lab observations for trend/search
- Basic diagnostic report, condition, medication, allergy, encounter, and document records
- Manual FHIR JSON import fallback
- Read-only local assistant with source citations

PDF, CSV, and XLSX parsing are stored as fallback metadata in this pass and can be expanded into structured adapters next.
