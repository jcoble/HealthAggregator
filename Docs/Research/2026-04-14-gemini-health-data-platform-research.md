# Architectural Blueprint for a Personal Health Record Aggregator and Autonomous Agentic AI System

> **Source:** Gemini deep research report, imported from `Health Data Platform & AI Agent.docx`, 2026-04-14. This is an external research artifact — per `CLAUDE.md` it is **CITED**, not VERIFIED. Claims about specific vendor behaviors, open-source projects (chartfold, longecho, arkiv), and library capabilities should be re-verified against primary sources before being used to drive implementation.
>
> **Scope note:** This project will not pursue direct LabCorp or Quest Diagnostics integrations — those sections are retained for context but excluded from the target feature set.

---

## Introduction and Industry Context

The contemporary healthcare technology landscape is defined by a paradox of unprecedented data generation paired with severe systemic fragmentation. Patients frequently interact with a diverse array of medical providers, specialists, diagnostic imaging centers, and commercial laboratories over the course of their lives. Consequently, critical medical data — ranging from longitudinal laboratory test results to operative reports and vital signs — becomes siloed within disparate Electronic Health Record (EHR) systems and proprietary vendor databases. For individuals seeking to assert proactive control over their medical management, this fragmentation presents a formidable barrier. While regulatory frameworks, most notably the United States 21st Century Cures Act and the implementation of the Fast Healthcare Interoperability Resources (FHIR) standard, have established a theoretical mandate for standardized application programming interfaces (APIs) and patient data access, the operational reality is substantially more complex. True data liquidity remains constrained by institutional friction, complex authentication protocols, and inconsistent interpretations of information blocking regulations.

Simultaneously, the rapid advancement of large language models (LLMs) and agentic artificial intelligence has introduced the transformative possibility of autonomous, highly capable digital healthcare assistants. These agents possess the potential to independently retrieve complex medical data, synthesize longitudinal clinical trends, generate predictive insights, and manage routine administrative tasks such as appointment scheduling and physician communication. However, deploying generic, off-the-shelf agent frameworks within the highly regulated healthcare domain frequently results in failure. The strict requirements for data privacy, the immense complexity of medical ontology, the necessity for deterministic and highly accurate data retrieval, and the risks associated with AI hallucination demand a highly specialized architectural approach.

This report provides a technical blueprint for the design, development, and deployment of a patient-controlled personal health record (PHR) aggregator integrated with a local database, a Model Context Protocol (MCP) server, and a proactive, autonomous AI agent. By systematically evaluating the specific API landscapes of major platforms including Epic MyChart, Quest Diagnostics, and LabCorp, analyzing optimal database schemas for clinical data, and detailing the cognitive architecture required for medical AI agents, this document outlines a methodology for developing an AI-driven healthcare platform. The ultimate objective is the realization of a system where the patient retains absolute sovereignty over their data, utilizing advanced computational tools to break down historical test results, identify overlooked clinical patterns, and autonomously orchestrate proactive medical care.

## The Healthcare Interoperability Landscape and Provider APIs

The foundational element of any personal health aggregation platform is the reliable and secure ingestion of longitudinal medical data. The United States healthcare system relies predominantly on the Health Level Seven (HL7) FHIR standard — specifically Release 4 (R4) — to facilitate RESTful interoperability. The FHIR standard utilizes distinct resource types (e.g., Patient, Encounter, Observation, DiagnosticReport) to represent clinical concepts, establishing a common semantic framework for data exchange. However, the accessibility of these APIs for individual, patient-directed application developers varies drastically across the industry, necessitating a granular analysis of each major vendor's ecosystem.

### Epic Systems and the SMART on FHIR Framework

Epic Systems represents the most mature, standardized, and accessible ecosystem for patient-directed API integration. Epic is a primary supporter of the HL7 FHIR standard and a key participant in the Argonaut Project and the Da Vinci Project. For a developer constructing a personal health aggregator, the Epic on FHIR platform provides a highly structured pathway for registering patient-facing applications.

The integration process relies on the SMART on FHIR protocol, which leverages the OAuth 2.0 framework to authenticate users and authorize applications without ever exposing the underlying user credentials to the third-party application. The implementation workflow involves several distinct phases:

1. The application developer completes client registration on the Epic on FHIR portal to obtain a `client_id`, which uniquely identifies the application to authentication servers across the entire Epic community.
2. During registration, the developer specifies one or more `redirect_uri` endpoints, which confirm the application's identity and are utilized to validate and redirect authentication requests.
3. When the patient initiates the data retrieval process, the application triggers an OAuth 2.0 authorization code flow, redirecting the user to their specific healthcare provider's MyChart login interface.
4. Upon successful authentication, the authorization server returns a secure code to the application's predefined `redirect_uri`.
5. The application then exchanges this code for a cryptographic access token, which is subsequently used to authenticate standard HTTP GET requests against the provider's FHIR REST endpoints.

Crucially, Epic's FHIR implementation strictly enforces patient-facing security contexts. When an application queries the `Patient` resource or the `Observation` resource containing critical laboratory results, the web service respects all configured service area restrictions and only returns data explicitly authorized for that specific patient's clinical profile. Epic's support for the United States Core Data for Interoperability (USCDI) data classes guarantees that the retrieved information is highly structured, comprehensive, and ready for relational mapping. Furthermore, Epic provides expansive developer resources, including sandbox test data environments and clear documentation on generating SMART Health Cards via QR codes or downloaded files.

### Quest Diagnostics and the Quanum FHIR API Constraints *(excluded from scope)*

Quest Diagnostics provides programmatic access to patient data via the Quanum EHR FHIR API. The Quanum API supports critical FHIR interactions including the search and read functions for the Observation resource, and is secured using SMART authorization protocols built on the OAuth 2.0 standard. However, unlike Epic's automated, self-service developer portal, Quest Diagnostics enforces a highly restrictive, manual onboarding process — developers must download, complete, and submit a formal PDF document. The Terms of Use for the Quanum API explicitly stipulate that the developer is acting merely as a "conduit to transfer Protected Health Information." The industry consensus indicates that Quest Diagnostics prioritizes strategic partnerships and large corporate clients rather than individual, patient-directed software development.

**Scope decision:** This integration is not pursued in this project.

### LabCorp, Information Blocking Challenges, and EDI Legacy *(excluded from scope)*

Laboratory Corporation of America Holdings (LabCorp) presents the most challenging integration environment for individual developers. Historically, LabCorp has structured its data exchange infrastructure around legacy EDI systems tailored for high-volume, enterprise clinical networks. Despite advanced enterprise capabilities, LabCorp's posture toward independent developers seeking to leverage the FHIR API for patient-directed applications has been historically restrictive. Independent developers attempting to register patient-facing applications with LabCorp have documented significant resistance, with formal Information Blocking Complaints (e.g., complaint IB-2820) filed against the laboratory network.

**Scope decision:** This integration is not pursued in this project.

### Data Source / Vendor Comparison

| Data Source / Vendor | API Standard | Authentication Model | Developer Access Accessibility | Primary Integration Model |
|---|---|---|---|---|
| **Epic MyChart** (in scope) | HL7 FHIR R4 | SMART on FHIR (OAuth 2.0) | High (self-service portal) | Direct API integration, SMART Health Cards |
| Quest Diagnostics (out of scope) | Quanum FHIR API | OAuth 2.0 | Low (manual PDF application) | Enterprise B2B, HIE integration, LexisNexis |
| LabCorp (out of scope) | Proprietary FHIR / EDI | Varied (FTP/SSH/SSL/OAuth) | Very Low (information-blocking reports) | Legacy EDI for hospitals, closed patient portal |

### Strategic Intermediaries and Alternative Ingestion Pathways

Given the friction associated with direct commercial lab integration, alternative ingestion pathways remain viable:

1. **Enterprise aggregators** such as 1upHealth or Human API pre-negotiate connections with tens of thousands of healthcare endpoints and provide a unified, normalized RESTful API. Their B2B pricing places them out of reach for a solo developer.
2. **Manual parsing of legally mandated data exports** — patient portals are required to offer comprehensive data exports (CDA R2 XML or raw FHIR JSON bundles). The open-source `chartfold` project demonstrates this paradigm: the user downloads their records from Epic MyChart (CDA XML or MHTML exports), MEDITECH Expanse (bulk CCDA XML and FHIR JSON), and Athenahealth (FHIR R4 XML ambulatory summaries), and passes these files into the application's local ingestion engine. This methodology ensures comprehensive data consolidation without the bureaucratic overhead of developer registration.

## Database Architecture: Relational Modeling for Clinical Complexity

Once the longitudinal medical data is extracted from the disparate source systems, it must be stored in a highly structured manner that facilitates both human analytical review and complex programmatic querying by artificial intelligence. The FHIR data model is highly denormalized and heavily reliant on nested reference chains — a single laboratory result involves an `Observation` resource that links back to an `Encounter`, which links to a `Patient`, a `Practitioner`, and a `DiagnosticReport`. Storing raw FHIR JSON in a document database presents severe analytical limitations; a highly structured, strongly typed relational database is mandatory to support deep medical reasoning by an AI agent.

### PostgreSQL vs SQLite

- **PostgreSQL** is the gold standard for enterprise healthcare applications — zero downtime, absolute data integrity, strict transactional consistency, role-based access control, and hybrid JSONB storage via tools like FHIRBase. Appropriate for cloud-deployed, multi-tenant applications.
- **SQLite "local-first"** offers compelling advantages for a personal health aggregator: a patient's complete medical history can be consolidated into a single, highly portable, durable file. This embraces the "files as ground truth" paradigm — databases as versionable, inspectable files that can be backed up, archived, or transmitted. SQLite is highly optimized for the read-heavy analytical workloads typical of AI interactions. Most importantly, SQLite provides critical security boundaries when exposed to LLMs; the connection can be strictly sandboxed at the engine level (e.g., `?mode=ro` URI parameter plus the SQLite authorizer callback) to prevent accidental or malicious data modification.

### Comprehensive Schema Design and Clinical Normalization

A comprehensive schema, derived from the `chartfold` project, requires approximately 17 distinct tables. The architecture uses a three-stage ingestion pipeline: the **Source Parser** reads the raw XML/JSON files; the **Adapter** normalizes the disparate data into `UnifiedRecords` dataclasses; and the **DB Loader** executes idempotent database transactions.

| Schema Category | Core Tables | Clinical Purpose and Normalization Requirements |
|---|---|---|
| **Core identity & encounters** | `patients`, `encounters`, `documents` | `encounters` is the central nervous system — every clinical event links back to a specific encounter. Cross-source deduplication uses composite keys (date + provider). `documents` maintains a strict audit trail linking data back to the original FHIR payload or source system. |
| **Clinical observations** | `lab_results`, `vitals` | Extract `test_name`, quantitative value, unit of measure, reference ranges. Capture LOINC codes for semantic interoperability ("WBC" from Quest = "White Blood Cell Count" from LabCorp). `vitals` separated for high-frequency time-series data. |
| **Medications & conditions** | `medications`, `allergies`, `conditions` | `medications` includes "multi-source badges" identifying same drug recorded by multiple providers (medication reconciliation). `conditions` uses ICD-10 and SNOMED-CT codes to maintain a persistent problem list with onset date and current status. |
| **Deep clinical narrative** | `clinical_notes`, `pathology_reports`, `imaging_reports`, `genetic_variants` | Unstructured narrative text — physician progress notes, discharge summaries, surgical operative reports, genomic assays. Stored separately from tabular labs so the LLM can perform summarization and context extraction beyond what structured data provides. |
| **System & annotations** | `load_log`, `notes`, `analyses`, `source_assets` | `load_log` maintains an immutable audit trail. `notes` and `analyses` provide writable surfaces where the user and AI agent store generated insights without altering the read-only clinical truths. `source_assets` maps relational data back to physical PDFs or scanned documents. |

**Integrity requirement:** Ingestion operations must be strictly idempotent — UPSERT based on natural keys so re-running a load for a specific source replaces or updates its previous data rather than duplicating it.

## The Model Context Protocol (MCP) in Clinical Systems

Historically, integrating LLMs with complex external databases required rigid hard-coded API wrappers, brittle prompt-chaining, or simplistic RAG pipelines that failed to grasp the nuanced relationships in medical data. The **Model Context Protocol (MCP)** is an open standard that allows AI models to dynamically discover, select, and interact with external data sources and tools through a standardized client-server architecture.

In this paradigm, the LLM (Claude Desktop, Cursor, custom applications) acts as the intelligent client while the local healthcare application acts as the MCP Server, exposing specific, highly constrained "tools" and "resources" that the LLM can autonomously invoke.

### "Composition Over Integration"

By utilizing an MCP server, the cognitive load of data retrieval, formatting, and analysis shifts entirely to the LLM's reasoning capabilities. The developer exposes a set of discrete functional primitives; the LLM reads the database schema, formulates optimal SQL queries, executes the tools, interprets results, and recursively decides next steps.

Based on the `chartfold` implementation, a robust healthcare MCP server exposes approximately **25 tools** across four categories:

#### 1. Database exploration and SQL execution

- **`get_schema`** — returns the `CREATE TABLE` DDL for the entire database. Foundational resource for the LLM to understand table relationships, column types, and constraints.
- **`get_database_summary`** — high-level statistical overview of table row counts and the chronological history of data loads. The AI's mandatory starting point for any broad inquiry.
- **`run_sql`** — the analytical escape hatch. Permits the AI to execute arbitrary custom SQL for questions that fall outside predefined tools.

**Security imperative:** the database connection utilized by the MCP server **must be enforced at the core engine level as strictly read-only**. In SQLite this means opening the connection with `?mode=ro` and using the SQLite authorizer callback to permanently block `INSERT`, `UPDATE`, `DROP`, `DELETE`. Application-layer restrictions (`PRAGMA query_only`) are insufficient.

#### 2. Analytical and longitudinal tools

- **`query_labs`** — retrieves structured lab results, filtering by test name, date range, source system, or LOINC code.
- **`get_lab_series_tool`** — cross-source time-series array for a specific biological marker. **Automatically normalizes disparate units of measure** (e.g., mg/dL → mmol/L) so trend math is accurate across labs.
- **`get_abnormal_labs_tool`** — filters for any result flagged abnormal, out-of-bounds, or critical.
- **`reconcile_medications_tool`** — cross-source medication reconciliation. Identifies conflicting dosages, duplicate prescriptions from different providers, and potential adverse drug-drug interactions.

#### 3. Clinical synthesis and visit preparation

- **`get_timeline`** — unified chronological event timeline interleaving encounters, procedures, imaging, labs, and pathology reports.
- **`get_visit_diff`** — diff comparison of database state between two dates. What new results, medications, or notes have been added since the last visit with a specific physician?
- **`get_visit_prep`** — pre-appointment summary bundle synthesizing recent abnormal labs, ongoing chronic conditions, and recent imaging into an actionable summary for a busy physician.
- **`get_surgical_timeline`** — isolates surgical procedures, linked to related pre-op pathology, intra-op imaging, and post-op medications.

#### 4. Persistent agent memory and state

- **`save_analysis` / `list_analyses`** — write structured insights back to a dedicated, writable `analyses` table (e.g., a prioritized list of questions for an oncologist, a metabolic trend summary).
- **`save_note` / `search_notes_personal`** — create, retrieve, search, and modify user-generated annotations, symptom tracking, and contextual metadata without altering the foundational clinical records.

## Architecting the Autonomous Healthcare Agent

With the longitudinal data securely aggregated and the MCP server exposing the schema through standardized tools, the next architectural layer is the autonomous agent framework. The objective is a system capable of proactive health management — reminding of appointments, autonomously monitoring new lab results, maintaining long-term clinical context, and drafting precise correspondence to healthcare providers.

### Why generic frameworks fail for healthcare

Generalized agent frameworks route messages through centralized gateways and rely on plain-text "skills" or instruction manuals. For healthcare they exhibit:

- **Context bloat and massive token waste** — generalized framework prompts crowd out the dense clinical data the model needs.
- **Absence of adaptive episodic memory** — static instruction files with no long-term continuity.
- **Unacceptable security vulnerabilities** — exposure of local shell commands, leaked API keys, arbitrary script execution.

The recommendation: abandon bloated frameworks in favor of **special-purpose agents built from scratch using robust orchestration libraries** such as LangGraph or Pydantic-AI.

### Multi-layered cognitive architecture

**1. Planning and routing layer.** Parses complex multi-faceted commands into a DAG of discrete tasks. Example: *"Analyze my recent lipid panels over 2 years, compare to my current statin dosage, draft a message to Dr. Smith about potentially lowering my dose"* decomposes into:

- Task A — `get_lab_series_tool` for "Lipid Panel" and "LDL Cholesterol" over 24 months
- Task B — `get_medications` to verify exact current statin dosage and frequency
- Task C — evaluate quantitative results against medical guidelines (LLM pre-trained weights) to determine if dosage reduction is clinically logical
- Task D — generate formal, professionally toned draft communication synthesizing A, B, and C

**2. Execution and tool-calling layer.** Strictly typed Pydantic validation of tool inputs. Operates on a ReAct (Reasoning and Acting) loop — on a tool error, reads the error, adjusts SQL or args, retries without user intervention.

**3. Proactive event-driven triggers via FHIR Subscriptions.** The agent registers a `Subscription` resource with the EHR server defining criteria (e.g., `Observation` where patient ID matches and status = `final`). When a lab is finalized, the FHIR server dispatches a notification (REST hook, email, or WebSocket) to the agent's endpoint. The agent awakens, ingests the payload, invokes `get_lab_series_tool` to compare against historical baselines, and pushes a contextualized alert to the user.

**4. Automated communication and drafting.** By connecting to the provider EHR's `Communication` or `MessageHeader` resources, the agent can construct structured digital messages — e.g., detecting a statistically significant upward trend in fasting glucose over three tests and drafting a message to the PCP with exact dates, values, and LOINC codes. **Strict human-in-the-loop constraint for all outbound communication** — the agent stages the draft, the patient reviews and explicitly approves before transmission.

## Frontend Deployment and System Integration

### Web application architecture

The primary interface should be a Single Page Application using modern reactive frameworks. It features interactive dashboards for longitudinal lab trends, active medications, upcoming appointments, and a conversational interface for interacting with the agent.

To fulfill local-data-ownership, the architecture can optionally use `sql.js` or WebAssembly to load and query the SQLite database directly in the browser — meaning sensitive health data never leaves the user's local machine.

### Mobile accessibility

The web application can be deployed as a native Android (or iOS) application via Capacitor or React Native, gaining local system notifications for medication reminders, biometric unlock, and background listeners for FHIR Subscription webhooks.

### Durable data formats and archival

The platform should support robust, universally readable export formats. Two reference philosophies:

- **`arkiv`** — the entire relational database exported as flat JSONL (JSON Lines) files, accompanied by `schema.yaml` and `README.md`. Self-describing, plaintext-first, degrades gracefully.
- **`longecho`** — self-contained HTML SPA containing both the viewer and the embedded SQLite database in a single file, portable on encrypted USB.

## Conclusion

The strategic utilization of manual data exports, intermediary aggregators, and mandated patient access APIs provides a viable pathway to personal data liberation even where commercial diagnostic networks present deliberate hurdles. A locally executed architectural design combining a highly normalized SQLite relational database, the standardized tool-calling capabilities of the Model Context Protocol, and a domain-specific LangGraph-based AI agent transforms inert, fragmented health records into a dynamic, instantly queryable knowledge base. The resulting agent transcends a simple search tool: it proactively monitors clinical events via FHIR subscriptions, synthesizes longitudinal trends, identifies anomalous markers, and drafts precise physician correspondence under a human-in-the-loop safety constraint.
