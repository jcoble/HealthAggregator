<script lang="ts">
	import { cn } from '$lib/utils';
	import * as Card from '$lib/components/ui/card';
	import type { Component } from 'svelte';

	let { icon: IconComp, label, value, subtitle, accent = 'primary', href, testid }: {
		icon: Component;
		label: string;
		value: string | number;
		subtitle?: string;
		accent?: 'primary' | 'success' | 'warning' | 'muted';
		href?: string;
		testid?: string;
	} = $props();

	const accentBg = {
		primary: 'bg-[var(--primary)]/15 text-[var(--primary)]',
		success: 'bg-[var(--success)]/15 text-emerald-300',
		warning: 'bg-[var(--warning)]/15 text-amber-300',
		muted:   'bg-[var(--muted)] text-[var(--muted-foreground)]',
	};
</script>

{#snippet body()}
	<Card.Header class="flex flex-row items-center justify-between">
		<Card.Title class="text-sm font-medium text-[var(--muted-foreground)]">{label}</Card.Title>
		<div class={cn('flex h-9 w-9 items-center justify-center rounded-xl', accentBg[accent])}>
			<IconComp size={18} />
		</div>
	</Card.Header>
	<Card.Content>
		<div class="text-2xl font-semibold text-[var(--foreground)]">{value}</div>
		{#if subtitle}<div class="text-xs text-[var(--muted-foreground)] mt-1">{subtitle}</div>{/if}
	</Card.Content>
{/snippet}

{#if href}
	<a href={href} data-testid={testid} class="block transition hover:-translate-y-0.5">
		<Card.Root>
			{@render body()}
		</Card.Root>
	</a>
{:else}
	<div data-testid={testid}>
		<Card.Root>
			{@render body()}
		</Card.Root>
	</div>
{/if}
