const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'https://localhost:5310';

export type FetchLike = typeof fetch;

export type EpicOrganization = {
	id: string;
	name: string;
	fhirBaseUrl: string;
	isSandbox: boolean;
};

export type EpicOrganizationsResponse = {
	configured: boolean;
	organizations: EpicOrganization[];
};

export type EpicConnection = {
	id: number;
	organizationId: string;
	organizationName: string;
	fhirBaseUrl: string;
	patientId: string | null;
	connectedAt: string;
	lastSyncedAt: string | null;
	hasToken: boolean;
};

export type EpicConnectResponse = {
	organization: EpicOrganization;
	authorizationUrl: string | null;
	error: string | null;
	readyToAuthorize: boolean;
};

export type LabObservation = {
	id: number;
	sourceName: string;
	sourceSystem: string;
	fhirReference: string;
	loincCode: string | null;
	testName: string;
	numericValue: number | null;
	textValue: string | null;
	unit: string | null;
	referenceLow: number | null;
	referenceHigh: number | null;
	referenceText: string | null;
	interpretation: string | null;
	effectiveAt: string | null;
	status: string;
};

export type MedicationRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	medicationText: string | null;
	status: string | null;
	authoredAt: string | null;
};

export type ConditionRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	codeText: string | null;
	clinicalStatus: string | null;
	onsetAt: string | null;
	recordedAt: string | null;
};

export type AllergyRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	allergyText: string | null;
	clinicalStatus: string | null;
	recordedAt: string | null;
};

export type EncounterRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	typeText: string | null;
	status: string | null;
	startedAt: string | null;
	endedAt: string | null;
};

export type DocumentRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	typeText: string | null;
	status: string | null;
	documentedAt: string | null;
	contentUrl: string | null;
};

export type LabSeries = {
	key: string;
	name: string;
	loincCode: string | null;
	points: Array<{
		effectiveAt: string | null;
		numericValue: number | null;
		textValue: string | null;
		unit: string | null;
		referenceLow: number | null;
		referenceHigh: number | null;
		interpretation: string | null;
		sourceSystem: string;
		sourceName: string;
		fhirReference: string;
	}>;
};

export type TimelineItem = {
	kind: string;
	title: string;
	at: string | null;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	numericValue: number | null;
	unit: string | null;
};

export type ImportJob = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	status: string;
	startedAt: string;
	completedAt: string | null;
	sourceRecordsUpserted: number;
	labObservationsUpserted: number;
	error: string | null;
};

export type ImportSummary = {
	sourceRecordsUpserted: number;
	labObservationsUpserted: number;
	diagnosticReportsUpserted: number;
	patientsUpserted: number;
	conditionsUpserted: number;
	medicationsUpserted: number;
	allergiesUpserted: number;
	encountersUpserted: number;
	documentsUpserted: number;
};

export type FhirResourceSyncSummary = {
	resourceType: string;
	requestPath: string;
	summary: ImportSummary;
};

export type EpicSyncSummary = {
	total: ImportSummary;
	resources: FhirResourceSyncSummary[];
};

export type AssistantReply = {
	threadId: number;
	message: string;
	citations: Array<{
		fhirReference: string;
		sourceName: string;
		effectiveAt: string | null;
		testName: string;
		value: string;
		unit: string | null;
	}>;
};

export type LabsQuery = {
	search?: string;
	from?: string;
	to?: string;
	loinc?: string;
	source?: string;
	abnormal?: boolean;
};

export type RecordQuery = {
	search?: string;
	status?: string;
	from?: string;
	to?: string;
	source?: string;
};

export type TimelineQuery = {
	kind?: string;
	source?: string;
	from?: string;
	to?: string;
	take?: number;
};

function buildUrl(path: string, params?: Record<string, string | boolean | number | undefined>): string {
	if (!params) return path;
	const sp = new URLSearchParams();
	for (const [k, v] of Object.entries(params)) {
		if (v !== undefined && v !== '' && v !== false) sp.set(k, String(v));
	}
	const q = sp.toString();
	return q ? `${path}?${q}` : path;
}

async function fetchJson<T>(path: string, init?: RequestInit, f: FetchLike = fetch): Promise<T> {
	const response = await f(`${API_BASE}${path}`, init);
	if (!response.ok) {
		const text = await response.text();
		throw new Error(text || `Request failed with ${response.status}`);
	}
	return (await response.json()) as T;
}

export const api = {
	baseUrl: API_BASE,
	getOrganizations: (f?: FetchLike) =>
		fetchJson<EpicOrganizationsResponse>('/api/integrations/epic/organizations', undefined, f),
	getConnections: (f?: FetchLike) =>
		fetchJson<EpicConnection[]>('/api/integrations/epic/connections', undefined, f),
	connectEpic: (organizationId: string) =>
		fetchJson<EpicConnectResponse>(`/api/integrations/epic/connect?organizationId=${encodeURIComponent(organizationId)}`),
	syncConnection: (connectionId: number) =>
		fetchJson<EpicSyncSummary>(
			`/api/integrations/epic/sync?connectionId=${encodeURIComponent(connectionId)}`,
			{ method: 'POST' }
		),
	disconnectConnection: (id: number) =>
		fetchJson<{ id: number }>(`/api/integrations/epic/connections/${id}`, { method: 'DELETE' }),
	getLabs: (f?: FetchLike, q?: LabsQuery) =>
		fetchJson<LabObservation[]>(buildUrl('/api/labs', q), undefined, f),
	getLabSeries: (f?: FetchLike, q?: { loinc?: string; name?: string; source?: string }) =>
		fetchJson<LabSeries[]>(buildUrl('/api/labs/series', q), undefined, f),
	getMedications: (f?: FetchLike, q?: RecordQuery) =>
		fetchJson<MedicationRecord[]>(buildUrl('/api/medications', q), undefined, f),
	getConditions: (f?: FetchLike, q?: RecordQuery) =>
		fetchJson<ConditionRecord[]>(buildUrl('/api/conditions', q), undefined, f),
	getAllergies: (f?: FetchLike, q?: RecordQuery) =>
		fetchJson<AllergyRecord[]>(buildUrl('/api/allergies', q), undefined, f),
	getEncounters: (f?: FetchLike, q?: RecordQuery) =>
		fetchJson<EncounterRecord[]>(buildUrl('/api/encounters', q), undefined, f),
	getDocuments: (f?: FetchLike, q?: RecordQuery) =>
		fetchJson<DocumentRecord[]>(buildUrl('/api/documents', q), undefined, f),
	getTimeline: (f?: FetchLike, q?: TimelineQuery) =>
		fetchJson<TimelineItem[]>(buildUrl('/api/timeline', q), undefined, f),
	getImports: (f?: FetchLike) => fetchJson<ImportJob[]>('/api/imports', undefined, f),
	uploadImport: async (file: File) => {
		const body = new FormData();
		body.append('file', file);
		return fetchJson('/api/imports', { method: 'POST', body });
	},
	askAssistant: (message: string) =>
		fetchJson<AssistantReply>('/api/assistant/messages', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ message })
		})
};
