<script lang="ts">
	import type { EncounterRecord } from '$lib/api';
	import type { Column } from '$lib/components/data-table/types';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import DataTable from '$lib/components/data-table/DataTable.svelte';
	import DataTableSearch from '$lib/components/data-table/DataTableSearch.svelte';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import * as Select from '$lib/components/ui/select';
	import * as Table from '$lib/components/ui/table';
	import { invalidateAll } from '$app/navigation';
	import { updateFilters } from '$lib/utils/url-filters';
	import { getEmptyStateProps } from '$lib/utils/empty-state';
	import { formatDate } from '$lib/utils/format';

	let { data } = $props();

	const filtersActive = $derived(Object.values(data.params).some(v => v !== undefined && v !== ''));
	const sourcesCount = $derived(new Set(data.rows.map(r => r.sourceSystem)).size);

	const columns: Column<EncounterRecord>[] = [
		{ id: 'startedAt', header: 'Started', sortBy: r => r.startedAt ? new Date(r.startedAt) : null },
		{ id: 'typeText', header: 'Type', sortBy: r => r.typeText ?? '' },
		{ id: 'status', header: 'Status' },
		{ id: 'endedAt', header: 'Ended', hideOn: 'md' },
		{ id: 'source', header: 'Source' }
	];
</script>

<PageHeader
	title="Encounters"
	subtitle={`${data.rows.length} records from ${sourcesCount} source${sourcesCount === 1 ? '' : 's'}`}
>
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="encounters-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

<div class="flex flex-wrap gap-3 mb-4" data-testid="encounters-filters">
	<DataTableSearch value={data.params.search ?? ''} placeholder="Search encounters..." />
	<Select.Root
		type="single"
		value={data.params.source ?? ''}
		onValueChange={(v) => updateFilters({ source: v || null })}
	>
		<Select.Trigger class="w-48" data-testid="encounters-source-filter">
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

{#if data.rows.length === 0}
	{@const props = getEmptyStateProps('encounter', data.rows.length, filtersActive)}
	<EmptyState title={props.title} description={props.description} actionLabel={props.actionLabel} actionHref={props.actionHref} />
{:else}
	<DataTable {columns} data={data.rows} pageSize={50}>
		{#snippet row(item)}
			<Table.Cell>{formatDate(item.startedAt)}</Table.Cell>
			<Table.Cell>{item.typeText ?? '—'}</Table.Cell>
			<Table.Cell>
				{#if item.status}<Badge variant="secondary">{item.status}</Badge>{:else}—{/if}
			</Table.Cell>
			<Table.Cell class="hidden md:table-cell">{formatDate(item.endedAt)}</Table.Cell>
			<Table.Cell><SourceBadge sourceSystem={item.sourceSystem} sourceName={item.sourceName} /></Table.Cell>
		{/snippet}
	</DataTable>
{/if}
