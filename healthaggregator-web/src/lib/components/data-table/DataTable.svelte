<script lang="ts" generics="T">
	import { cn } from '$lib/utils';
	import * as Table from '$lib/components/ui/table';
	import { ChevronUp, ChevronDown } from '@lucide/svelte';
	import type { Column, DataTableProps } from './types';

	let { columns, data, row, pageSize = 50, loading = false, empty }: DataTableProps<T> = $props();

	let sortId = $state<string | null>(null);
	let sortDir = $state<'asc' | 'desc'>('desc');
	let pageIndex = $state(0);

	const sorted = $derived.by(() => {
		if (!sortId) return data;
		const col = columns.find(c => c.id === sortId);
		if (!col?.sortBy) return data;
		const by = col.sortBy;
		return [...data].sort((a, b) => {
			const av = by(a); const bv = by(b);
			if (av == null && bv == null) return 0;
			if (av == null) return 1;  // nulls to bottom
			if (bv == null) return -1;
			const cmp = av < bv ? -1 : av > bv ? 1 : 0;
			return sortDir === 'asc' ? cmp : -cmp;
		});
	});

	const pageCount = $derived(Math.max(1, Math.ceil(sorted.length / pageSize)));
	const start = $derived(pageIndex * pageSize);
	const rows = $derived(sorted.slice(start, start + pageSize));

	const HIDE_CLASS: Record<string, string> = {
		sm: 'hidden sm:table-cell',
		md: 'hidden md:table-cell',
		lg: 'hidden lg:table-cell',
	};

	function setSort(col: Column<T>) {
		if (!col.sortBy) return;
		if (sortId === col.id) {
			sortDir = sortDir === 'asc' ? 'desc' : 'asc';
		} else {
			sortId = col.id;
			sortDir = 'desc';
		}
	}
</script>

{#if loading}
	<div data-testid="data-table-loading" class="space-y-2">
		{#each Array(6) as _, i (i)}
			<div class="h-10 animate-pulse rounded bg-[var(--muted)]"></div>
		{/each}
	</div>
{:else if data.length === 0 && empty}
	{@render empty()}
{:else}
	<div data-testid="data-table" class="overflow-hidden rounded-lg border border-[var(--border)]">
		<Table.Root>
			<Table.Header>
				<Table.Row>
					{#each columns as col (col.id)}
						<Table.Head class={cn(col.hideOn && HIDE_CLASS[col.hideOn], col.widthClass)}>
							{#if col.sortBy}
								<button
									type="button"
									onclick={() => setSort(col)}
									class="flex items-center gap-1 hover:text-[var(--foreground)]"
									data-testid={`column-sort-${col.id}`}
								>
									{col.header}
									{#if sortId === col.id}
										{#if sortDir === 'asc'}<ChevronUp size={12} />{:else}<ChevronDown size={12} />{/if}
									{/if}
								</button>
							{:else}
								{col.header}
							{/if}
						</Table.Head>
					{/each}
				</Table.Row>
			</Table.Header>
			<Table.Body>
				{#each rows as item, i (i)}
					<Table.Row>
						{@render row(item)}
					</Table.Row>
				{/each}
			</Table.Body>
		</Table.Root>
	</div>

	{#if pageCount > 1}
		<div data-testid="data-table-pagination" class="flex items-center justify-between mt-3 text-sm text-[var(--muted-foreground)]">
			<span>{start + 1}-{Math.min(start + pageSize, sorted.length)} of {sorted.length}</span>
			<div class="flex gap-2">
				<button
					type="button"
					onclick={() => (pageIndex = Math.max(0, pageIndex - 1))}
					disabled={pageIndex === 0}
					data-testid="data-table-prev"
					class="rounded border border-[var(--border)] px-3 py-1 disabled:opacity-50"
				>Prev</button>
				<button
					type="button"
					onclick={() => (pageIndex = Math.min(pageCount - 1, pageIndex + 1))}
					disabled={pageIndex >= pageCount - 1}
					data-testid="data-table-next"
					class="rounded border border-[var(--border)] px-3 py-1 disabled:opacity-50"
				>Next</button>
			</div>
		</div>
	{/if}
{/if}
