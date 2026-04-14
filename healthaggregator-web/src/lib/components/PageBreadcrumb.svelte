<script lang="ts">
	import { page } from '$app/stores';
	import { ChevronRight, Home } from '@lucide/svelte';

	const ROUTE_LABELS: Record<string, string> = {
		'/':            'Overview',
		'/connections': 'Connections',
		'/imports':     'Imports',
		'/labs':        'Labs',
		'/vitals':      'Vitals',
		'/medications': 'Medications',
		'/conditions':  'Conditions',
		'/allergies':   'Allergies',
		'/encounters':  'Encounters',
		'/documents':   'Documents',
		'/timeline':    'Timeline',
		'/assistant':   'Assistant',
		'/settings':    'Settings',
	};

	const SECTION_LABELS: Record<string, string> = {
		'/labs':         'Records',
		'/vitals':       'Records',
		'/medications':  'Records',
		'/conditions':   'Records',
		'/allergies':    'Records',
		'/encounters':   'Records',
		'/documents':    'Records',
		'/timeline':     'Insights',
		'/assistant':    'Insights',
		'/connections':  'Data Sources',
		'/imports':      'Data Sources',
	};

	const path = $derived($page.url.pathname);
	const section = $derived(SECTION_LABELS[path]);
	const label = $derived(ROUTE_LABELS[path] ?? '');
</script>

<nav aria-label="Breadcrumb" data-testid="breadcrumb">
	<ol class="flex items-center space-x-1 text-sm text-[var(--muted-foreground)]">
		<li>
			<a href="/" class="flex items-center hover:text-[var(--foreground)] transition-colors" data-testid="breadcrumb-home">
				<Home class="h-4 w-4" />
			</a>
		</li>
		{#if section}
			<li class="flex items-center">
				<ChevronRight class="h-4 w-4 mx-1" />
				<span class="text-[var(--muted-foreground)]">{section}</span>
			</li>
		{/if}
		{#if label}
			<li class="flex items-center">
				<ChevronRight class="h-4 w-4 mx-1" />
				<span class="text-[var(--foreground)] font-medium">{label}</span>
			</li>
		{/if}
	</ol>
</nav>
