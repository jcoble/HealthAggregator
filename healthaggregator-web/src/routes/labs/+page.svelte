<script lang="ts">
	import type { LabObservation } from '$lib/api';
	import type { Column } from '$lib/components/data-table/types';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import DataTable from '$lib/components/data-table/DataTable.svelte';
	import DataTableSearch from '$lib/components/data-table/DataTableSearch.svelte';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as Select from '$lib/components/ui/select';
	import * as Table from '$lib/components/ui/table';
	import { invalidateAll } from '$app/navigation';
	import { updateFilters } from '$lib/utils/url-filters';
	import { getEmptyStateProps } from '$lib/utils/empty-state';
	import { formatDate } from '$lib/utils/format';
	import { sourceColor } from '$lib/utils/source-theme';

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

	const filtersActive = $derived(Object.values(data.params).some(v => v !== undefined && v !== ''));
	const sourcesCount = $derived(new Set(data.labs.map(l => l.sourceSystem)).size);

	// Trend: pick the most-recently-sampled series with numeric points for default selection
	let selectedKey = $state<string | null>(null);
	const selectedSeries = $derived.by(() => {
		if (!data.series.length) return null;
		const byKey = selectedKey ? data.series.find(s => s.key === selectedKey) : null;
		return byKey ?? data.series.find(s => s.points.some(p => p.numericValue != null)) ?? data.series[0];
	});

	const colorHex: Record<string, string> = {
		red: '#ef4444', blue: '#3b82f6', grey: '#71717a', amber: '#f59e0b',
		purple: '#a855f7', teal: '#14b8a6', pink: '#ec4899', orange: '#f97316',
		cyan: '#06b6d4', indigo: '#6366f1'
	};
	function pointColor(src: string) { return colorHex[sourceColor(src)] ?? colorHex.grey; }

	const sparkline = $derived.by(() => {
		if (!selectedSeries) return null;
		const numericPoints = selectedSeries.points
			.filter(p => p.effectiveAt != null && p.numericValue != null)
			.map(p => ({ t: new Date(p.effectiveAt!).getTime(), v: Number(p.numericValue), source: p.sourceSystem }))
			.sort((a, b) => a.t - b.t);
		if (numericPoints.length < 2) return null;
		const W = 400, H = 120, PAD = 12;
		const minT = numericPoints[0].t, maxT = numericPoints[numericPoints.length - 1].t;
		const vs = numericPoints.map(p => p.v);
		const minV = Math.min(...vs), maxV = Math.max(...vs);
		const spanT = Math.max(1, maxT - minT), spanV = Math.max(0.0001, maxV - minV);
		const pts = numericPoints.map(p => ({
			x: PAD + ((p.t - minT) / spanT) * (W - 2 * PAD),
			y: H - PAD - ((p.v - minV) / spanV) * (H - 2 * PAD),
			color: pointColor(p.source)
		}));
		const line = pts.map(p => `${p.x},${p.y}`).join(' ');
		return { W, H, pts, line, minV, maxV };
	});

	const legendSources = $derived.by(() => {
		if (!selectedSeries) return [];
		const set = new Set(selectedSeries.points.map(p => p.sourceSystem));
		return [...set];
	});

	const columns: Column<LabObservation>[] = [
		{ id: 'effectiveAt', header: 'Date', sortBy: r => r.effectiveAt ? new Date(r.effectiveAt) : null },
		{ id: 'testName', header: 'Test', sortBy: r => r.testName ?? '' },
		{ id: 'value', header: 'Value' },
		{ id: 'refRange', header: 'Ref range', hideOn: 'md' },
		{ id: 'flag', header: 'Flag' },
		{ id: 'source', header: 'Source' }
	];

	function clearFilters() {
		updateFilters({ search: null, from: null, to: null, loinc: null, source: null, abnormal: null });
	}
</script>

<PageHeader
	title="Labs"
	subtitle={`${data.labs.length} results from ${sourcesCount} source${sourcesCount === 1 ? '' : 's'}`}
>
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="labs-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

<div class="flex flex-wrap gap-3 mb-4 items-center" data-testid="labs-filters">
	<DataTableSearch value={data.params.search ?? ''} placeholder="Search test or LOINC..." />
	<Input
		type="date"
		value={data.params.from ?? ''}
		onchange={(e) => updateFilters({ from: (e.target as HTMLInputElement).value || null })}
		data-testid="labs-from"
		class="w-40"
	/>
	<Input
		type="date"
		value={data.params.to ?? ''}
		onchange={(e) => updateFilters({ to: (e.target as HTMLInputElement).value || null })}
		data-testid="labs-to"
		class="w-40"
	/>
	<Select.Root
		type="single"
		value={data.params.source ?? ''}
		onValueChange={(v) => updateFilters({ source: v || null })}
	>
		<Select.Trigger class="w-48" data-testid="labs-source-filter">
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
	<label class="flex items-center gap-2 text-sm" data-testid="labs-abnormal-wrapper">
		<Checkbox
			checked={data.params.abnormal === true}
			onCheckedChange={(v) => updateFilters({ abnormal: v ? 'true' : null })}
			data-testid="labs-abnormal-toggle"
		/>
		Abnormal only
	</label>
	{#if filtersActive}
		<Button variant="ghost" size="sm" onclick={clearFilters} data-testid="labs-clear-filters">Clear</Button>
	{/if}
</div>

<div class="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
	<div>
		{#if data.labs.length === 0}
			{@const props = getEmptyStateProps('lab', data.labs.length, filtersActive)}
			<EmptyState title={props.title} description={props.description} actionLabel={props.actionLabel} actionHref={props.actionHref} />
		{:else}
			<DataTable {columns} data={data.labs} pageSize={50}>
				{#snippet row(item)}
					<Table.Cell>{formatDate(item.effectiveAt)}</Table.Cell>
					<Table.Cell>
						<div class="font-medium">{item.testName}</div>
						{#if item.loincCode}
							<div class="text-xs text-[var(--muted-foreground)]">{item.loincCode}</div>
						{/if}
					</Table.Cell>
					<Table.Cell>
						{#if item.numericValue != null}
							<span class="font-medium">{item.numericValue}</span>{#if item.unit}<span class="text-xs text-[var(--muted-foreground)] ml-1">{item.unit}</span>{/if}
						{:else if item.textValue}
							{item.textValue}
						{:else}—{/if}
					</Table.Cell>
					<Table.Cell class="hidden md:table-cell text-xs text-[var(--muted-foreground)]">
						{#if item.referenceLow != null || item.referenceHigh != null}
							{item.referenceLow ?? ''}–{item.referenceHigh ?? ''}
						{:else if item.referenceText}
							{item.referenceText}
						{:else}—{/if}
					</Table.Cell>
					<Table.Cell>
						{#if isAbnormal(item)}<Badge variant="warning">abnormal</Badge>{:else}—{/if}
					</Table.Cell>
					<Table.Cell><SourceBadge sourceSystem={item.sourceSystem} sourceName={item.sourceName} /></Table.Cell>
				{/snippet}
			</DataTable>
		{/if}
	</div>

	<aside class="lg:sticky lg:top-20">
		<div class="rounded-lg border border-[var(--border)] bg-[var(--card)] p-4">
			<div class="text-sm font-semibold mb-3">Trend</div>
			{#if data.series.length === 0}
				<div class="text-sm text-[var(--muted-foreground)]">No numeric series available.</div>
			{:else}
				<Select.Root
					type="single"
					value={selectedSeries?.key ?? ''}
					onValueChange={(v) => (selectedKey = v || null)}
				>
					<Select.Trigger class="w-full" data-testid="labs-trend-select">
						{selectedSeries ? selectedSeries.name : 'Select a test'}
					</Select.Trigger>
					<Select.Content>
						{#each data.series as s (s.key)}
							<Select.Item value={s.key}>{s.name}{s.loincCode ? ` (${s.loincCode})` : ''}</Select.Item>
						{/each}
					</Select.Content>
				</Select.Root>

				<div class="mt-3" data-testid="labs-trend-chart">
					{#if sparkline}
						<svg viewBox="0 0 {sparkline.W} {sparkline.H}" class="w-full">
							<polyline
								fill="none"
								stroke="var(--muted-foreground)"
								stroke-opacity="0.4"
								stroke-width="1.5"
								points={sparkline.line}
							/>
							{#each sparkline.pts as p, i (i)}
								<circle cx={p.x} cy={p.y} r="3.5" fill={p.color} />
							{/each}
						</svg>
						<div class="flex justify-between text-xs text-[var(--muted-foreground)] mt-1">
							<span>{sparkline.minV}</span>
							<span>{sparkline.maxV}</span>
						</div>
					{:else}
						<div class="text-sm text-[var(--muted-foreground)]">Need at least 2 numeric points to draw a trend.</div>
					{/if}
				</div>

				{#if legendSources.length > 0}
					<div class="flex flex-wrap gap-2 mt-3">
						{#each legendSources as src (src)}
							<span class="inline-flex items-center gap-1.5 text-xs text-[var(--muted-foreground)]">
								<span class="inline-block w-2 h-2 rounded-full" style:background-color={pointColor(src)}></span>
								{src}
							</span>
						{/each}
					</div>
				{/if}
			{/if}
		</div>
	</aside>
</div>
