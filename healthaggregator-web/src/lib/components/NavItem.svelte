<script lang="ts">
	import { page } from '$app/stores';
	import { cn } from '$lib/utils';
	import type { Component } from 'svelte';

	let { href, icon: IconComp, label, testid }: {
		href: string;
		icon: Component;
		label: string;
		testid?: string;
	} = $props();

	const active = $derived($page.url.pathname === href);
</script>

<a
	href={href}
	data-testid={testid ?? `nav-${label.toLowerCase().replace(/\s+/g, '-')}`}
	class={cn(
		'flex items-center gap-2.5 rounded-md px-3 py-1.5 text-sm transition',
		active
			? 'bg-[var(--primary)]/15 text-[var(--primary)] font-medium'
			: 'text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]'
	)}
>
	<IconComp size={16} />
	<span class="truncate">{label}</span>
</a>
