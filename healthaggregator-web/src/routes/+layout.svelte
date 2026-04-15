<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';
	import AppShell from '$lib/components/AppShell.svelte';
	import NavigationLoader from '$lib/components/NavigationLoader.svelte';
	import { Toaster } from '$lib/components/ui/sonner';
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { toast } from 'svelte-sonner';

	let { children } = $props();

	onMount(() => {
		const params = $page.url.searchParams;
		const epic = params.get('epic');
		if (epic === 'connected') {
			toast.success('Connected to Epic');
			goto('/connections', { replaceState: true });
		} else if (epic === 'error') {
			const reason = params.get('reason') ?? 'unknown error';
			toast.error(`Epic connection failed: ${reason}`);
			goto('/', { replaceState: true });
		}
	});
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
<NavigationLoader />
<Toaster position="top-right" />
<AppShell>
	{@render children()}
</AppShell>
