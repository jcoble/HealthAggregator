<script lang="ts">
	import { cn } from '$lib/utils';
	import { ChevronDown } from '@lucide/svelte';
	import { onMount } from 'svelte';
	import type { Component, Snippet } from 'svelte';

	let { id, icon: IconComp, title, children }: {
		id: string;
		icon: Component;
		title: string;
		children: Snippet;
	} = $props();

	let expanded = $state(true);

	onMount(() => {
		const stored = localStorage.getItem(`healthaggregator.nav.${id}`);
		if (stored !== null) expanded = stored === 'true';
	});

	function toggle() {
		expanded = !expanded;
		localStorage.setItem(`healthaggregator.nav.${id}`, String(expanded));
	}
</script>

<div data-testid={`nav-group-${id}`} class="mt-3">
	<button
		type="button"
		onclick={toggle}
		data-testid={`nav-group-toggle-${id}`}
		class="flex w-full items-center justify-between px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
	>
		<span class="flex items-center gap-2"><IconComp size={14} /> {title}</span>
		<ChevronDown size={14} class={cn('transition', !expanded && '-rotate-90')} />
	</button>
	{#if expanded}
		<div class="mt-1 space-y-0.5">{@render children()}</div>
	{/if}
</div>
