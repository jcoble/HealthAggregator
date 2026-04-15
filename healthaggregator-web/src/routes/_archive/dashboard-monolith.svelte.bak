<script lang="ts">
	import { onMount } from 'svelte';
	import {
		Activity,
		Bot,
		Database,
		FileUp,
		HeartPulse,
		Link,
		RefreshCw,
		ShieldCheck,
		Upload
	} from '@lucide/svelte';
	import {
		api,
		type AssistantReply,
		type EpicConnection,
		type EpicOrganization,
		type EpicSyncSummary,
		type ImportJob,
		type LabObservation,
		type LabSeries,
		type TimelineItem
	} from '$lib/api';

	let organizations = $state<EpicOrganization[]>([]);
	let connections = $state<EpicConnection[]>([]);
	let labs = $state<LabObservation[]>([]);
	let series = $state<LabSeries[]>([]);
	let timeline = $state<TimelineItem[]>([]);
	let imports = $state<ImportJob[]>([]);
	let selectedOrganizationId = $state('epic-sandbox');
	let epicConfigured = $state(false);
	let loading = $state(true);
	let busy = $state(false);
	let uploadMessage = $state('');
	let error = $state('');
	let epicStatus = $state('');
	let syncMessage = $state('');
	let assistantQuestion = $state('Show my A1C trend and cite the source values.');
	let assistantReply = $state<AssistantReply | null>(null);
	let lastSyncSummary = $state<EpicSyncSummary | null>(null);

	const latestLabs = $derived(labs.slice(0, 8));
	const abnormalLabs = $derived(labs.filter(isAbnormal));
	const selectedSeries = $derived(series.find((item) => item.points.some((point) => point.numericValue !== null)));
	const connected = $derived(connections.length > 0);

	onMount(() => {
		void initializeDashboard();
	});

	async function initializeDashboard() {
		const callbackResult = readEpicCallbackResult();
		if (callbackResult?.status === 'connected') {
			epicStatus = 'MyChart connected. Syncing latest records now.';
		}

		await refreshAll(callbackResult?.status !== 'error');

		if (callbackResult?.status === 'error') {
			error = callbackResult.reason ? `Epic authorization failed: ${callbackResult.reason}` : 'Epic authorization failed.';
		}

		if (callbackResult?.status === 'connected') {
			await syncLatest('MyChart connected. Syncing latest records now.');
		}
	}

	async function refreshAll(clearError = true) {
		loading = true;
		if (clearError) error = '';
		try {
			const orgResponse = await api.getOrganizations();
			organizations = orgResponse.organizations;
			epicConfigured = orgResponse.configured;
			selectedOrganizationId = organizations[0]?.id ?? 'epic-sandbox';

			const [connectionResponse, labResponse, seriesResponse, timelineResponse, importResponse] =
				await Promise.all([
					api.getConnections(),
					api.getLabs(),
					api.getLabSeries(),
					api.getTimeline(),
					api.getImports()
				]);

			connections = connectionResponse;
			labs = labResponse;
			series = seriesResponse;
			timeline = timelineResponse;
			imports = importResponse;
		} catch (err) {
			error = err instanceof Error ? err.message : 'Unable to reach the API.';
		} finally {
			loading = false;
		}
	}

	async function connectMyChart() {
		busy = true;
		error = '';
		try {
			const response = await api.connectEpic(selectedOrganizationId);
			if (response.authorizationUrl) {
				window.location.href = response.authorizationUrl;
				return;
			}
			error = response.error ?? 'Epic authorization is not ready.';
		} catch (err) {
			error = readableError(err);
		} finally {
			busy = false;
		}
	}

	async function syncLatest(statusMessage = 'Syncing latest MyChart records.') {
		const connection = connections[0];
		if (!connection) return;

		busy = true;
		error = '';
		syncMessage = statusMessage;
		try {
			lastSyncSummary = await api.syncConnection(connection.id);
			syncMessage = syncSummaryText(lastSyncSummary);
			epicStatus = 'MyChart is connected.';
			await refreshAll();
		} catch (err) {
			error = readableError(err);
			syncMessage = '';
		} finally {
			busy = false;
		}
	}

	async function uploadFile(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;

		busy = true;
		error = '';
		uploadMessage = '';
		try {
			await api.uploadImport(file);
			uploadMessage = `${file.name} was imported or stored as fallback metadata.`;
			await refreshAll();
		} catch (err) {
			error = readableError(err);
		} finally {
			busy = false;
			input.value = '';
		}
	}

	async function askAssistant() {
		if (!assistantQuestion.trim()) return;

		busy = true;
		error = '';
		try {
			assistantReply = await api.askAssistant(assistantQuestion);
		} catch (err) {
			error = readableError(err);
		} finally {
			busy = false;
		}
	}

	function isAbnormal(lab: LabObservation) {
		if (lab.interpretation) return true;
		if (lab.numericValue === null) return false;
		if (lab.referenceLow !== null && lab.numericValue < lab.referenceLow) return true;
		return lab.referenceHigh !== null && lab.numericValue > lab.referenceHigh;
	}

	function formatDate(value: string | null) {
		if (!value) return 'Unknown';
		return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(
			new Date(value)
		);
	}

	function formatValue(lab: LabObservation) {
		const value = lab.numericValue ?? lab.textValue ?? 'No value';
		return `${value}${lab.unit ? ` ${lab.unit}` : ''}`;
	}

	function sparklinePoints(item: LabSeries | undefined) {
		const points = item?.points.filter((point) => point.numericValue !== null) ?? [];
		if (points.length === 0) return '';

		const values = points.map((point) => Number(point.numericValue));
		const min = Math.min(...values);
		const max = Math.max(...values);
		const range = max - min || 1;
		const width = 520;
		const height = 86;
		const step = points.length === 1 ? width : width / (points.length - 1);

		return points
			.map((point, index) => {
				const x = index * step;
				const y = height - ((Number(point.numericValue) - min) / range) * height + 10;
				return `${x},${y}`;
			})
			.join(' ');
	}

	function readableError(err: unknown) {
		if (!(err instanceof Error)) return 'Request failed.';
		try {
			const parsed = JSON.parse(err.message);
			return parsed.error ?? parsed.message ?? err.message;
		} catch {
			return err.message;
		}
	}

	function readEpicCallbackResult() {
		const params = new URLSearchParams(window.location.search);
		const status = params.get('epic');
		if (!status) return null;

		const reason = params.get('reason');
		params.delete('epic');
		params.delete('reason');
		const query = params.toString();
		window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}${window.location.hash}`);

		return { status, reason };
	}

	function syncSummaryText(summary: EpicSyncSummary) {
		const total = summary.total;
		return `Sync complete: ${total.sourceRecordsUpserted} source record(s), ${total.labObservationsUpserted} lab result(s), ${total.diagnosticReportsUpserted} report(s).`;
	}
</script>

<svelte:head>
	<title>Health Aggregator</title>
	<meta
		name="description"
		content="Local-first personal health record prototype with MyChart integration, lab trends, and read-only AI assistance."
	/>
</svelte:head>

<div class="app-shell">
	<aside class="sidebar">
		<div class="brand">
			<div class="brand-mark"><HeartPulse size={19} /></div>
			<div>
				<div>HealthAggregator</div>
				<div class="muted" style="font-size: 12px">Local-first records</div>
			</div>
		</div>
		<nav class="nav-list" aria-label="Primary">
			<a class="nav-item active" href="#connect"><Link size={16} /> MyChart</a>
			<a class="nav-item" href="#labs"><Activity size={16} /> Labs</a>
			<a class="nav-item" href="#imports"><FileUp size={16} /> Imports</a>
			<a class="nav-item" href="#assistant"><Bot size={16} /> Assistant</a>
		</nav>
	</aside>

	<main class="content">
		<div class="topbar">
			<div>
				<p class="eyebrow">MyChart first prototype</p>
				<h1>One local record for labs, reports, medications, and source-backed questions.</h1>
				<p class="muted">
					Connect Epic/MyChart when a client ID is configured. Upload FHIR exports as backup when a provider
					does not expose everything through the API.
				</p>
			</div>
			<button class="button secondary" onclick={() => refreshAll()} disabled={loading || busy}>
				<RefreshCw size={16} /> Refresh
			</button>
		</div>

		{#if error}
			<div class="error" role="alert">{error}</div>
		{/if}
		{#if epicStatus}
			<div class="alert success">{epicStatus}</div>
		{/if}
		{#if syncMessage}
			<div class="alert success">{syncMessage}</div>
		{/if}

		<section class="grid grid-3" aria-label="Record status">
			<div class="stat">
				<div class="stat-value">{labs.length}</div>
				<div class="stat-label">Lab results synced or imported</div>
			</div>
			<div class="stat">
				<div class="stat-value">{abnormalLabs.length}</div>
				<div class="stat-label">Flagged or out-of-range labs</div>
			</div>
			<div class="stat">
				<div class="stat-value">{timeline.length}</div>
				<div class="stat-label">Timeline events</div>
			</div>
		</section>

		<section id="connect" class="grid grid-2" style="margin-top: 16px">
			<div class="panel flush">
				<img
					class="panel-image"
					src="https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=1200&q=80"
					alt="Clinician reviewing digital health records"
				/>
				<div class="panel-body">
					<h2>Connect MyChart</h2>
					<p class="muted">
						This starts a SMART on FHIR OAuth flow against Epic. The backend requests read-only patient scopes
						for labs, reports, conditions, medications, allergies, encounters, documents, and binary attachments.
					</p>

					{#if !epicConfigured}
						<div class="alert">
							Set <strong>Epic:ClientId</strong> in the API configuration before launching the Epic authorization
							flow. The sandbox organization is already configured.
						</div>
					{/if}

					<div class="grid" style="margin-top: 12px">
						<label>
							<span class="muted">Health system</span>
							<select class="select" bind:value={selectedOrganizationId}>
								{#each organizations as organization}
									<option value={organization.id}>{organization.name}</option>
								{/each}
							</select>
						</label>
						<div class="button-row">
							<button class="button" onclick={connectMyChart} disabled={busy || organizations.length === 0}>
								<Link size={16} /> Connect MyChart
							</button>
							<a
								class="button secondary"
								href={`${api.baseUrl}/api/integrations/epic/authorize?organizationId=${encodeURIComponent(selectedOrganizationId)}`}
							>
								Open sandbox flow
							</a>
							<button class="button secondary" onclick={() => syncLatest()} disabled={busy || !connected}>
								<Database size={16} /> Sync latest
							</button>
						</div>
					</div>
				</div>
			</div>

			<div class="panel">
				<h2>Connection status</h2>
				{#if connections.length === 0}
					<p class="muted">No MyChart connection is stored yet.</p>
				{:else}
					{#each connections as connection}
						<div class="stat" style="margin-bottom: 10px">
							<div class="button-row" style="justify-content: space-between">
								<strong>{connection.organizationName}</strong>
								<span class="badge success">read-only</span>
							</div>
							<p class="muted" style="margin: 8px 0 0">
								Patient {connection.patientId ?? 'unknown'} · last sync {formatDate(connection.lastSyncedAt)}
							</p>
						</div>
					{/each}
				{/if}
				<div class="badge"><ShieldCheck size={13} /> Local SQLite storage</div>
				{#if lastSyncSummary}
					<div class="sync-summary">
						<div class="button-row" style="justify-content: space-between">
							<strong>Last sync details</strong>
							<span class="badge success">{lastSyncSummary.total.sourceRecordsUpserted} source records</span>
						</div>
						{#each lastSyncSummary.resources as resource}
							<div class="sync-row">
								<span>{resource.resourceType}</span>
								<span class="muted">
									{resource.summary.sourceRecordsUpserted} raw · {resource.summary.labObservationsUpserted} labs ·
									{resource.summary.diagnosticReportsUpserted} reports
								</span>
							</div>
						{/each}
					</div>
				{/if}
			</div>
		</section>

		<section id="labs" class="grid grid-2" style="margin-top: 16px">
			<div class="panel">
				<h2>Lab trend</h2>
				{#if selectedSeries}
					<div class="button-row" style="justify-content: space-between">
						<div>
							<strong>{selectedSeries.name}</strong>
							<div class="muted">{selectedSeries.loincCode ?? 'No LOINC'} · {selectedSeries.points.length} result(s)</div>
						</div>
						<span class="badge">source-backed</span>
					</div>
					<svg class="sparkline" viewBox="0 0 520 110" role="img" aria-label="Selected lab trend">
						<polyline
							points={sparklinePoints(selectedSeries)}
							fill="none"
							stroke="#22c55e"
							stroke-width="3"
							stroke-linejoin="round"
							stroke-linecap="round"
						/>
					</svg>
				{:else}
					<p class="muted">No numeric lab series yet. Sync MyChart or import a FHIR bundle.</p>
				{/if}
			</div>

			<div class="panel">
				<h2>Recent timeline</h2>
				<div class="timeline">
					{#each timeline.slice(0, 8) as item}
						<div class="timeline-item">
							<div class="muted">{formatDate(item.at)}</div>
							<div>
								<strong>{item.title}</strong>
								<div class="muted">{item.kind} · {item.sourceName}</div>
							</div>
						</div>
					{:else}
						<p class="muted">No timeline events yet.</p>
					{/each}
				</div>
			</div>
		</section>

		<section class="panel" style="margin-top: 16px">
			<h2>Latest labs</h2>
			<div class="table-wrap">
				<table>
					<thead>
						<tr>
							<th>Date</th>
							<th>Test</th>
							<th>Value</th>
							<th>Range</th>
							<th>Flag</th>
							<th>Source</th>
						</tr>
					</thead>
					<tbody>
						{#each latestLabs as lab}
							<tr>
								<td>{formatDate(lab.effectiveAt)}</td>
								<td>{lab.testName}<div class="muted">{lab.loincCode ?? lab.fhirReference}</div></td>
								<td>{formatValue(lab)}</td>
								<td>{lab.referenceText ?? [lab.referenceLow, lab.referenceHigh].filter((value) => value !== null).join(' - ')}</td>
								<td>
									{#if isAbnormal(lab)}
										<span class="badge warning">{lab.interpretation ?? 'out of range'}</span>
									{:else}
										<span class="badge">normal</span>
									{/if}
								</td>
								<td>{lab.sourceName}</td>
							</tr>
						{:else}
							<tr>
								<td colspan="6" class="muted">No labs available yet.</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</section>

		<section id="imports" class="grid grid-2" style="margin-top: 16px">
			<div class="panel">
				<h2>Manual import fallback</h2>
				<p class="muted">
					Use this for FHIR JSON exports or records MyChart does not expose cleanly. PDF, CSV, and XLSX parsing
					can be added next; non-JSON uploads are stored as source metadata for now.
				</p>
				<label class="button secondary">
					<Upload size={16} /> Upload record file
					<input type="file" accept=".json,.txt,.xml,.pdf,.csv,.xlsx" onchange={uploadFile} style="display: none" />
				</label>
				{#if uploadMessage}
					<p class="muted" style="margin-top: 10px">{uploadMessage}</p>
				{/if}
			</div>

			<div class="panel">
				<h2>Import history</h2>
				<div class="timeline">
					{#each imports.slice(0, 6) as item}
						<div class="timeline-item">
							<div class="muted">{formatDate(item.startedAt)}</div>
							<div>
								<strong>{item.sourceName}</strong>
								<div class="muted">
									{item.status} · {item.sourceRecordsUpserted} source records · {item.labObservationsUpserted} labs
								</div>
							</div>
						</div>
					{:else}
						<p class="muted">No imports yet.</p>
					{/each}
				</div>
			</div>
		</section>

		<section id="assistant" class="panel" style="margin-top: 16px">
			<h2>Read-only assistant</h2>
			<p class="muted">
				The assistant only queries normalized local records. It cannot write clinical data, send messages, or make care
				changes.
			</p>
			<div class="grid">
				<textarea class="textarea" bind:value={assistantQuestion}></textarea>
				<div class="button-row">
					<button class="button" onclick={askAssistant} disabled={busy}>
						<Bot size={16} /> Ask from local records
					</button>
				</div>
				{#if assistantReply}
					<div class="assistant-answer">{assistantReply.message}</div>
					{#if assistantReply.citations.length > 0}
						<div>
							<h3>Citations</h3>
							{#each assistantReply.citations as citation}
								<span class="badge" style="margin: 0 6px 6px 0">
									{citation.testName} · {formatDate(citation.effectiveAt)} · {citation.value}{citation.unit ? ` ${citation.unit}` : ''}
								</span>
							{/each}
						</div>
					{/if}
				{/if}
			</div>
		</section>
	</main>
</div>
