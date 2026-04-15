<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import DataTableSearch from '$lib/components/data-table/DataTableSearch.svelte';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import * as Select from '$lib/components/ui/select';
	import { invalidateAll } from '$app/navigation';
	import { updateFilters } from '$lib/utils/url-filters';
	import { formatDate } from '$lib/utils/format';
	import { Activity, FileSearch, Stethoscope, Pill, ShieldAlert, CalendarClock, FileText, History } from '@lucide/svelte';

	let { data } = $props();

	const KIND_ICONS: Record<string, any> = {
		lab: Activity,
		report: FileSearch,
		condition: Stethoscope,
		medication: Pill,
		allergy: ShieldAlert,
		encounter: CalendarClock,
		document: FileText
	};

	const KIND_VARIANTS: Record<string, 'default' | 'secondary' | 'success' | 'warning' | 'destructive' | 'outline'> = {
		lab: 'default',
		report: 'secondary',
		condition: 'warning',
		medication: 'success',
		allergy: 'destructive',
		encounter: 'outline',
		document: 'outline'
	};

	const sourcesCount = $derived(new Set(data.items.map(i => i.sourceSystem)).size);

	function loadMore() {
		updateFilters({ take: (data.params.take ?? 300) + 300 });
	}
</script>

<PageHeader
	title="Timeline"
	subtitle={`${data.items.length} events across ${sourcesCount} source${sourcesCount === 1 ? '' : 's'}`}
>
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="timeline-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

<div class="flex flex-wrap gap-3 mb-4" data-testid="timeline-filters">
	<DataTableSearch value="" placeholder="(search not wired yet)" />
	<Select.Root
		type="single"
		value={data.params.kind ?? ''}
		onValueChange={(v) => updateFilters({ kind: v || null })}
	>
		<Select.Trigger class="w-48" data-testid="timeline-kind-filter">
			{data.params.kind ? data.params.kind : 'All kinds'}
		</Select.Trigger>
		<Select.Content>
			<Select.Item value="">All kinds</Select.Item>
			<Select.Item value="lab">Labs</Select.Item>
			<Select.Item value="report">Reports</Select.Item>
			<Select.Item value="condition">Conditions</Select.Item>
			<Select.Item value="medication">Medications</Select.Item>
			<Select.Item value="allergy">Allergies</Select.Item>
			<Select.Item value="encounter">Encounters</Select.Item>
			<Select.Item value="document">Documents</Select.Item>
		</Select.Content>
	</Select.Root>
	<Select.Root
		type="single"
		value={data.params.source ?? ''}
		onValueChange={(v) => updateFilters({ source: v || null })}
	>
		<Select.Trigger class="w-48" data-testid="timeline-source-filter">
			{data.params.source
				? (data.sources.find(s => s.id === data.params.source)?.name ?? data.params.source)
				: 'All sources'}
		</Select.Trigger>
		<Select.Content>
			<Select.Item value="">All sources</Select.Item>
			{#each data.sources as org (org.id)}
				<Select.Item value={org.id}>{org.name}</Select.Item>
			{/each}
			<Select.Item value="manual-upload">Manual Upload</Select.Item>
		</Select.Content>
	</Select.Root>
</div>

{#if data.items.length === 0}
	<EmptyState
		icon={History}
		title="No timeline events"
		description="Sync a connection or import a bundle to populate your timeline."
		actionLabel="Manage Connections"
		actionHref="/connections"
	/>
{:else}
	<ul class="divide-y divide-[var(--border)] rounded-lg border border-[var(--border)] bg-[var(--card)]">
		{#each data.items as item, i (item.fhirReference + i)}
			{@const Icon = KIND_ICONS[item.kind] ?? History}
			<li
				class="flex items-center gap-4 px-4 py-3 hover:bg-[var(--muted)]/30"
				data-testid={`timeline-row-${item.fhirReference}`}
			>
				<span class="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--muted)] text-[var(--muted-foreground)] shrink-0">
					<Icon size={16} />
				</span>
				<div class="w-24 text-xs text-[var(--muted-foreground)] shrink-0">{formatDate(item.at)}</div>
				<Badge variant={KIND_VARIANTS[item.kind] ?? 'outline'}>{item.kind}</Badge>
				<div class="flex-1 min-w-0 truncate text-sm">{item.title}</div>
				<SourceBadge sourceSystem={item.sourceSystem} sourceName={item.sourceName} />
			</li>
		{/each}
	</ul>

	{#if data.items.length >= (data.params.take ?? 300)}
		<div class="mt-4 text-center">
			<Button variant="secondary" onclick={loadMore} data-testid="timeline-load-more">Load more</Button>
		</div>
	{/if}
{/if}
