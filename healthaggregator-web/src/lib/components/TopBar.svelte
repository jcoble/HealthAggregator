<script lang="ts">
	import PageBreadcrumb from './PageBreadcrumb.svelte';
	import type { Snippet } from 'svelte';

	let { actions }: { actions?: Snippet } = $props();
</script>

<div data-testid="topbar" class="flex h-14 items-center justify-between border-b border-[var(--border)] px-6">
	<PageBreadcrumb />
	{#if actions}
		<div class="flex items-center gap-2">{@render actions()}</div>
	{/if}
</div>
