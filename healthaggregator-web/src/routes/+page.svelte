<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import StatCard from '$lib/components/StatCard.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { invalidateAll } from '$app/navigation';
	import { formatDate, formatRelativeTime } from '$lib/utils/format';
	import { Activity, ShieldAlert, History, Plug, HeartPulse } from '@lucide/svelte';
	import type { LabObservation } from '$lib/api';

	let { data } = $props();

	const ABNORMAL_PREFIXES = ['HH', 'LL', 'H', 'L', 'A'];
	function isAbnormal(lab: LabObservation): boolean {
		const interp = lab.interpretation?.toUpperCase() ?? '';
		if (interp && ABNORMAL_PREFIXES.some(p => interp.startsWith(p))) return true;
		if (lab.numericValue != null) {
			if (lab.referenceLow != null && lab.numericValue < lab.referenceLow) return true;
			if (lab.referenceHigh != null && lab.numericValue > lab.referenceHigh) return true;
		}
		return false;
	}

	const KIND_VARIANTS: Record<string, 'default' | 'secondary' | 'success' | 'warning' | 'destructive' | 'outline'> = {
		lab: 'default',
		report: 'secondary',
		condition: 'warning',
		medication: 'success',
		allergy: 'destructive',
		encounter: 'outline',
		document: 'outline'
	};

	const sourcesAccent = $derived(
		data.connections.some(c => c.hasToken) ? 'success' as const : 'muted' as const
	);
</script>

<PageHeader title="Overview" subtitle="Your health records at a glance">
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="dashboard-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

{#if data.connections.length === 0}
	<EmptyState
		icon={HeartPulse}
		title="Connect your first health record"
		description="HealthAggregator pulls your Epic MyChart records into a single local database that only you can see."
	>
		{#snippet children()}
			<a href="/connections" data-testid="empty-connect-cta" class="inline-flex items-center justify-center rounded-md bg-[var(--primary)] px-4 py-2 text-sm font-medium text-[var(--primary-foreground)] transition hover:opacity-90">
				Connect Epic MyChart →
			</a>
		{/snippet}
	</EmptyState>
{:else}
	<!-- Stat Cards -->
	<div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-6">
		<StatCard
			icon={Activity}
			label="Labs"
			value={data.stats.labCount}
			subtitle="All-time"
			accent="primary"
			href="/labs"
			testid="stat-labs"
		/>
		<StatCard
			icon={ShieldAlert}
			label="Abnormal labs"
			value={data.stats.abnormalCount}
			subtitle="Flagged results"
			accent="warning"
			href="/labs?abnormal=true"
			testid="stat-abnormal"
		/>
		<StatCard
			icon={History}
			label="Timeline events"
			value={data.stats.timelineCount}
			subtitle="All records"
			accent="primary"
			href="/timeline"
			testid="stat-timeline"
		/>
		<StatCard
			icon={Plug}
			label="Sources"
			value={data.stats.sourcesConnected}
			subtitle="Orgs connected"
			accent={sourcesAccent}
			href="/connections"
			testid="stat-sources"
		/>
	</div>

	<!-- Two-column grid: Connection Status + Recent Labs -->
	<div class="grid gap-6 lg:grid-cols-2 mb-6">
		<!-- Connection Status -->
		<Card.Root>
			<Card.Header>
				<Card.Title>Connection Status</Card.Title>
			</Card.Header>
			<Card.Content class="p-0">
				<ul class="divide-y divide-[var(--border)]">
					{#each data.orgs as org (org.id)}
						{@const connection = data.connections.find(c => c.organizationId === org.id)}
						{@const connected = connection?.hasToken}
						<li class="flex items-center gap-3 px-4 py-3" data-testid={`dashboard-connection-row-${org.id}`}>
							<span class={`w-2 h-2 rounded-full shrink-0 ${connected ? 'bg-[var(--success)]' : 'bg-[var(--muted-foreground)]'}`}></span>
							<SourceBadge sourceSystem={org.id} sourceName={org.name} />
							<div class="flex-1 min-w-0">
								<div class="text-sm font-medium truncate">{org.name}</div>
								<div class="text-xs text-[var(--muted-foreground)] mt-0.5">
									{#if connected && connection}
										Last sync {formatRelativeTime(connection.lastSyncedAt)}
									{:else}
										Not connected
									{/if}
								</div>
							</div>
						</li>
					{/each}
				</ul>
			</Card.Content>
			<Card.Footer>
				<a href="/connections" data-testid="dashboard-manage-connections" class="text-sm text-[var(--primary)] hover:underline">Manage →</a>
			</Card.Footer>
		</Card.Root>

		<!-- Recent Labs -->
		<Card.Root>
			<Card.Header>
				<Card.Title>Recent labs</Card.Title>
			</Card.Header>
			<Card.Content class="p-0">
				<ul class="divide-y divide-[var(--border)]">
					{#each data.recentLabs as lab (lab.id)}
						<li class="flex items-center gap-3 px-4 py-3">
							<div class="flex-1 min-w-0">
								<div class="text-sm font-medium truncate">{lab.testName}</div>
								<div class="text-xs text-[var(--muted-foreground)] mt-0.5">
									{#if lab.numericValue != null}
										{lab.numericValue}{lab.unit ? ` ${lab.unit}` : ''}
									{:else if lab.textValue}
										{lab.textValue}
									{/if}
								</div>
							</div>
							<SourceBadge sourceSystem={lab.sourceSystem} sourceName={lab.sourceName} />
							{#if isAbnormal(lab)}
								<Badge variant="warning">abnormal</Badge>
							{/if}
							<span class="text-xs text-[var(--muted-foreground)] shrink-0">{formatRelativeTime(lab.effectiveAt)}</span>
						</li>
					{/each}
				</ul>
			</Card.Content>
			<Card.Footer>
				<a href="/labs" data-testid="dashboard-view-all-labs" class="text-sm text-[var(--primary)] hover:underline">View all →</a>
			</Card.Footer>
		</Card.Root>
	</div>

	<!-- Full-width Recent Timeline -->
	<Card.Root>
		<Card.Header>
			<Card.Title>Recent timeline</Card.Title>
		</Card.Header>
		<Card.Content class="p-0">
			<ul class="divide-y divide-[var(--border)]">
				{#each data.recentTimeline as item, i (item.fhirReference + i)}
					<li class="flex items-center gap-4 px-4 py-3">
						<div class="w-24 text-xs text-[var(--muted-foreground)] shrink-0">{formatDate(item.at)}</div>
						<Badge variant={KIND_VARIANTS[item.kind] ?? 'outline'}>{item.kind}</Badge>
						<div class="flex-1 min-w-0 text-sm truncate">{item.title}</div>
						<SourceBadge sourceSystem={item.sourceSystem} sourceName={item.sourceName} />
					</li>
				{/each}
			</ul>
		</Card.Content>
		<Card.Footer>
			<a href="/timeline" class="text-sm text-[var(--primary)] hover:underline">Open full timeline →</a>
		</Card.Footer>
	</Card.Root>
{/if}
