<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import ErrorBanner from '$lib/components/ErrorBanner.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import * as Card from '$lib/components/ui/card';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { api, type EpicConnection, type EpicOrganization } from '$lib/api';
	import { invalidateAll } from '$app/navigation';
	import { toast } from 'svelte-sonner';
	import { formatRelativeTime } from '$lib/utils/format';
	import { MoreVertical, Link as LinkIcon, RefreshCw, Unplug } from '@lucide/svelte';

	let { data } = $props();

	let syncingId = $state<number | null>(null);
	let connectingId = $state<string | null>(null);
	let disconnectTarget = $state<EpicConnection | null>(null);
	let disconnectOpen = $state(false);
	let lastSyncSummary = $state<any | null>(null);

	const connectedSum = $derived(data.connections.filter(c => c.hasToken).length);

	function findConnection(orgId: string): EpicConnection | undefined {
		return data.connections.find(c => c.organizationId === orgId);
	}

	async function onConnect(org: EpicOrganization) {
		if (!org.fhirBaseUrl) {
			toast.error(`${org.name}: FhirBaseUrl is not configured in appsettings.json`);
			return;
		}
		connectingId = org.id;
		try {
			const response = await api.connectEpic(org.id);
			if (response.readyToAuthorize && response.authorizationUrl) {
				window.location.href = response.authorizationUrl;
			} else {
				toast.error(response.error ?? 'Failed to build authorization URL');
			}
		} catch (e) {
			toast.error(e instanceof Error ? e.message : 'Connect failed');
		} finally {
			connectingId = null;
		}
	}

	async function onSync(connection: EpicConnection) {
		syncingId = connection.id;
		try {
			lastSyncSummary = await api.syncConnection(connection.id);
			toast.success(`Synced ${connection.organizationName}`);
			await invalidateAll();
		} catch (e) {
			toast.error(e instanceof Error ? e.message : 'Sync failed');
		} finally {
			syncingId = null;
		}
	}

	function requestDisconnect(connection: EpicConnection) {
		disconnectTarget = connection;
		disconnectOpen = true;
	}

	async function confirmDisconnect() {
		if (!disconnectTarget) return;
		try {
			await api.disconnectConnection(disconnectTarget.id);
			toast.success(`Disconnected from ${disconnectTarget.organizationName}`);
			await invalidateAll();
		} catch (e) {
			toast.error(e instanceof Error ? e.message : 'Disconnect failed');
		} finally {
			disconnectOpen = false;
			disconnectTarget = null;
		}
	}
</script>

<PageHeader
	title="Connections"
	subtitle={`${connectedSum}/${data.orgs.length} sources connected`}
>
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="connections-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

{#if !data.configured}
	<div class="mb-6">
		<ErrorBanner variant="warning" title="Epic Client ID is not configured">
			Set it before connecting:
			<code class="ml-1 bg-[var(--muted)] px-1.5 py-0.5 rounded text-xs">dotnet user-secrets set "Epic:ClientId" "&lt;id&gt;" --project HealthAggregator.Api</code>
		</ErrorBanner>
	</div>
{/if}

<Card.Root>
	<Card.Header>
		<Card.Title>Available sources</Card.Title>
		<Card.Description>Epic-using organizations configured in appsettings.json. Data from every connected source is merged into the same local database, tagged by its source.</Card.Description>
	</Card.Header>
	<Card.Content class="p-0">
		<ul class="divide-y divide-[var(--border)]">
			{#each data.orgs as org (org.id)}
				{@const connection = findConnection(org.id)}
				{@const connected = connection?.hasToken}
				{@const missingUrl = !org.fhirBaseUrl}
				<li class="flex items-center gap-4 px-4 py-3" data-testid={`org-row-${org.id}`}>
					<span class={`w-2 h-2 rounded-full ${connected ? 'bg-[var(--success)]' : 'bg-[var(--muted-foreground)]'}`}></span>
					<SourceBadge sourceSystem={org.id} sourceName={org.name} />
					<div class="flex-1 min-w-0">
						<div class="flex items-center gap-2 text-sm">
							<span class="font-medium">{org.name}</span>
							{#if org.isSandbox}<Badge variant="secondary">sandbox</Badge>{/if}
						</div>
						{#if missingUrl}
							<div class="text-xs text-[var(--muted-foreground)] mt-0.5">Configure FhirBaseUrl in appsettings.json to enable</div>
						{:else if connection}
							<div class="text-xs text-[var(--muted-foreground)] mt-0.5">
								Last sync {formatRelativeTime(connection.lastSyncedAt)} · Patient ID {connection.patientId ?? '—'}
							</div>
						{:else}
							<div class="text-xs text-[var(--muted-foreground)] mt-0.5">Not connected</div>
						{/if}
					</div>
					{#if connected && connection}
						<Button
							variant="secondary"
							onclick={() => onSync(connection)}
							disabled={syncingId === connection.id}
							data-testid={`org-sync-${org.id}`}
						>
							<RefreshCw size={14} class={syncingId === connection.id ? 'animate-spin' : ''} />
							Sync
						</Button>
						<DropdownMenu.Root>
							<DropdownMenu.Trigger
								class="inline-flex h-9 w-9 items-center justify-center rounded-md hover:bg-[var(--muted)]"
								data-testid={`org-menu-${org.id}`}
								aria-label="More actions"
							>
								<MoreVertical size={16} />
							</DropdownMenu.Trigger>
							<DropdownMenu.Content align="end">
								<DropdownMenu.Item onclick={() => onSync(connection)} data-testid={`org-menu-resync-${org.id}`}>
									Re-sync
								</DropdownMenu.Item>
								<DropdownMenu.Separator />
								<DropdownMenu.Item
									variant="destructive"
									onclick={() => requestDisconnect(connection)}
									data-testid={`org-menu-disconnect-${org.id}`}
								>
									Disconnect
								</DropdownMenu.Item>
							</DropdownMenu.Content>
						</DropdownMenu.Root>
					{:else}
						<Button
							onclick={() => onConnect(org)}
							disabled={!data.configured || missingUrl || connectingId === org.id}
							data-testid={`org-connect-${org.id}`}
						>
							<LinkIcon size={14} />
							Connect
						</Button>
					{/if}
				</li>
			{/each}
		</ul>
	</Card.Content>
</Card.Root>

{#if lastSyncSummary}
	<Card.Root class="mt-6" data-testid="last-sync-summary">
		<Card.Header>
			<Card.Title>Recent sync details</Card.Title>
			<Card.Description>Per-resource breakdown from the most recent sync</Card.Description>
		</Card.Header>
		<Card.Content>
			<div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm mb-4">
				<div><span class="text-[var(--muted-foreground)]">Source records</span><div class="font-semibold">{lastSyncSummary.total?.sourceRecordsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Labs</span><div class="font-semibold">{lastSyncSummary.total?.labObservationsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Reports</span><div class="font-semibold">{lastSyncSummary.total?.diagnosticReportsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Patients</span><div class="font-semibold">{lastSyncSummary.total?.patientsUpserted ?? 0}</div></div>
			</div>
			<ul class="space-y-1 text-xs text-[var(--muted-foreground)]">
				{#each lastSyncSummary.resources ?? [] as r (r.requestPath)}
					<li><code>{r.requestPath}</code> → {r.summary.sourceRecordsUpserted} records, {r.summary.labObservationsUpserted} labs</li>
				{/each}
			</ul>
		</Card.Content>
	</Card.Root>
{/if}

{#if disconnectTarget}
	<ConfirmDialog
		bind:open={disconnectOpen}
		title={`Disconnect from ${disconnectTarget.organizationName}?`}
		description="Stored records remain. Only the OAuth tokens are cleared."
		confirmLabel="Disconnect"
		variant="destructive"
		onconfirm={confirmDisconnect}
		oncancel={() => { disconnectOpen = false; disconnectTarget = null; }}
		testId="disconnect-dialog"
	/>
{/if}
