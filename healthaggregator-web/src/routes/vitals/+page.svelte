<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import { HeartPulse } from '@lucide/svelte';
</script>

<PageHeader title="Vitals" />
<EmptyState
	icon={HeartPulse}
	title="Vitals coverage coming in Stream C"
	description="Vital-sign Observations are already being pulled from Epic into the raw SourceRecord table, but the structured VitalsObservation entity, ingestion path, and list / trend UI land in the next spec. Vitals + medications + labs correlated over time are load-bearing for the diagnostic assistant goal."
/>
