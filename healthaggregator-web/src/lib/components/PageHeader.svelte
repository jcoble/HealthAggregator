<script lang="ts">
	import type { Snippet } from 'svelte';

	let { title, subtitle, actions }: {
		title: string;
		subtitle?: string;
		actions?: Snippet;
	} = $props();
</script>

<header data-testid="page-header" class="flex flex-wrap items-start justify-between gap-4 mb-6">
	<div class="min-w-0">
		<h1 data-testid="page-header-title" class="text-2xl font-semibold text-[var(--foreground)]">{title}</h1>
		{#if subtitle}
			<p data-testid="page-header-subtitle" class="text-sm text-[var(--muted-foreground)] mt-1">{subtitle}</p>
		{/if}
	</div>
	{#if actions}
		<div class="flex flex-wrap items-center gap-2">
			{@render actions()}
		</div>
	{/if}
</header>
