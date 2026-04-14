<script lang="ts">
	import { Loader2 } from '@lucide/svelte';
	import { cn } from '$lib/utils';

	type Props = {
		message?: string;
		class?: string;
	};

	let { message = 'Loading...', class: className }: Props = $props();
</script>

<div class={cn('flex flex-col items-center justify-center py-12', className)} data-testid="loading-state">
	<Loader2 class="h-8 w-8 animate-spin text-primary" />
	{#if message}
		<p class="mt-3 text-sm text-muted-foreground">{message}</p>
	{/if}
</div>
