<script lang="ts">
	import { Input } from '$lib/components/ui/input';
	import { Search } from '@lucide/svelte';
	import { updateFilters } from '$lib/utils/url-filters';

	let { value = '', placeholder = 'Search...', paramName = 'search' }: {
		value?: string;
		placeholder?: string;
		paramName?: string;
	} = $props();

	let local = $state(value);
	let timer: ReturnType<typeof setTimeout> | null = null;

	$effect(() => {
		local = value;
	});

	function onInput(e: Event) {
		local = (e.target as HTMLInputElement).value;
		if (timer) clearTimeout(timer);
		timer = setTimeout(() => updateFilters({ [paramName]: local || null }), 300);
	}
</script>

<div class="relative">
	<Search size={14} class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)] pointer-events-none" />
	<Input
		type="text"
		value={local}
		oninput={onInput}
		placeholder={placeholder}
		data-testid="data-table-search"
		class="pl-9"
	/>
</div>
