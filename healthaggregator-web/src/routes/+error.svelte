<script lang="ts">
	import { page } from '$app/stores';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { goto, invalidateAll } from '$app/navigation';
</script>

<div class="max-w-xl mx-auto mt-12" data-testid="page-error">
	<Card.Root>
		<Card.Header>
			<Card.Title>Something went wrong</Card.Title>
			<Card.Description>We hit an error loading this page.</Card.Description>
		</Card.Header>
		<Card.Content>
			<pre class="whitespace-pre-wrap text-sm text-[var(--muted-foreground)]">{$page.error?.message ?? 'Unknown error'}</pre>
		</Card.Content>
		<Card.Footer class="flex gap-2">
			<Button onclick={() => invalidateAll()} data-testid="page-error-retry">Retry</Button>
			<Button variant="outline" onclick={() => goto('/')} data-testid="page-error-home">Go to Dashboard</Button>
		</Card.Footer>
	</Card.Root>
</div>
