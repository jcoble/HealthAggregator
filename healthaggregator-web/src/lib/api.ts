const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'https://localhost:5310';

export type EpicOrganization = {
	id: string;
	name: string;
	fhirBaseUrl: string;
	authorizationEndpoint: string;
	tokenEndpoint: string;
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
		sourceName: string;
		fhirReference: string;
	}>;
};

export type TimelineItem = {
	kind: string;
	title: string;
	at: string | null;
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

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
	const response = await fetch(`${API_BASE}${path}`, init);
	if (!response.ok) {
		const text = await response.text();
		throw new Error(text || `Request failed with ${response.status}`);
	}

	return (await response.json()) as T;
}

export const api = {
	baseUrl: API_BASE,
	getOrganizations: () => fetchJson<EpicOrganizationsResponse>('/api/integrations/epic/organizations'),
	getConnections: () => fetchJson<EpicConnection[]>('/api/integrations/epic/connections'),
	connectEpic: (organizationId: string) =>
		fetchJson<EpicConnectResponse>(`/api/integrations/epic/connect?organizationId=${encodeURIComponent(organizationId)}`),
	syncConnection: (connectionId: number) =>
		fetchJson<EpicSyncSummary>('/api/integrations/epic/sync?connectionId=' + encodeURIComponent(connectionId), {
			method: 'POST'
		}),
	getLabs: () => fetchJson<LabObservation[]>('/api/labs'),
	getLabSeries: () => fetchJson<LabSeries[]>('/api/labs/series'),
	getTimeline: () => fetchJson<TimelineItem[]>('/api/timeline'),
	getImports: () => fetchJson<ImportJob[]>('/api/imports'),
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
