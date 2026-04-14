<script lang="ts">
	import { InboxIcon } from '@lucide/svelte';
	import { Button } from '$lib/components/ui/button';
	import { cn } from '$lib/utils';
	import type { Snippet, Component } from 'svelte';

	type Props = {
		title?: string;
		description?: string;
		icon?: Component<{ class?: string }>;
		actionLabel?: string;
		actionHref?: string;
		onaction?: () => void;
		class?: string;
		children?: Snippet;
	};

	let {
		title = 'No results found',
		description = 'There are no items to display.',
		icon: Icon = InboxIcon,
		actionLabel,
		actionHref,
		onaction,
		class: className,
		children
	}: Props = $props();
</script>

<div class={cn('flex flex-col items-center justify-center py-12 text-center', className)} data-testid="empty-state">
	<div class="rounded-full bg-muted p-4">
		<Icon class="h-8 w-8 text-muted-foreground" />
	</div>
	<h3 class="mt-4 text-lg font-medium">{title}</h3>
	<p class="mt-1 text-sm text-muted-foreground max-w-sm">{description}</p>
	{#if actionLabel}
		{#if actionHref}
			<Button href={actionHref} class="mt-4" data-testid="empty-state-action">
				{actionLabel}
			</Button>
		{:else if onaction}
			<Button onclick={onaction} class="mt-4" data-testid="empty-state-action">
				{actionLabel}
			</Button>
		{/if}
	{/if}
	{#if children}
		<div class="mt-4">
			{@render children()}
		</div>
	{/if}
</div>
