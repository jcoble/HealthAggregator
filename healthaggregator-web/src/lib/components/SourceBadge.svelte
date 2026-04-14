<script lang="ts">
	import { cn } from '$lib/utils';
	import { sourceBadgeClasses } from '$lib/utils/source-theme';

	let { sourceSystem, sourceName, class: className = '' }: {
		sourceSystem: string;
		sourceName?: string;
		class?: string;
	} = $props();

	const label = $derived(sourceName ?? sourceSystem);
</script>

<span
	data-testid={`source-badge-${sourceSystem}`}
	class={cn(
		'inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium',
		sourceBadgeClasses(sourceSystem),
		className
	)}
>
	{label}
</span>
