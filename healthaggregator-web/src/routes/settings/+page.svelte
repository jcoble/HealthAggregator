<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import * as Card from '$lib/components/ui/card';
	import * as Tabs from '$lib/components/ui/tabs';
	import { Badge } from '$lib/components/ui/badge';
	import SourceBadge from '$lib/components/SourceBadge.svelte';

	let { data } = $props();
</script>

<PageHeader title="Settings" />

<Tabs.Root value="general" class="w-full">
	<Tabs.List>
		<Tabs.Trigger value="general" data-testid="settings-tab-general">General</Tabs.Trigger>
		<Tabs.Trigger value="epic" data-testid="settings-tab-epic">Epic</Tabs.Trigger>
		<Tabs.Trigger value="data" data-testid="settings-tab-data">Data</Tabs.Trigger>
		<Tabs.Trigger value="about" data-testid="settings-tab-about">About</Tabs.Trigger>
	</Tabs.List>

	<Tabs.Content value="general">
		<EmptyState title="Preferences coming later" description="General app preferences will land in a future spec." />
	</Tabs.Content>

	<Tabs.Content value="epic">
		<Card.Root>
			<Card.Header>
				<Card.Title>Epic integration</Card.Title>
				<Card.Description>SMART on FHIR client status and configured organizations</Card.Description>
			</Card.Header>
			<Card.Content class="space-y-4">
				<div class="flex items-center gap-2">
					<span class="text-[var(--muted-foreground)]">Client ID:</span>
					{#if data.orgs.configured}
						<Badge variant="success" data-testid="settings-epic-client-set">Set</Badge>
					{:else}
						<Badge variant="warning" data-testid="settings-epic-client-unset">Not set</Badge>
					{/if}
				</div>
				{#if !data.orgs.configured}
					<p class="text-sm text-[var(--muted-foreground)]">
						Set it via user-secrets:
						<code class="bg-[var(--muted)] px-1.5 py-0.5 rounded text-xs">dotnet user-secrets set "Epic:ClientId" "&lt;id&gt;" --project HealthAggregator.Api</code>
					</p>
				{/if}
				<div>
					<div class="text-sm text-[var(--muted-foreground)] mb-2">Configured organizations:</div>
					<ul class="space-y-2">
						{#each data.orgs.organizations as org (org.id)}
							<li class="flex items-center gap-2" data-testid={`settings-org-${org.id}`}>
								<SourceBadge sourceSystem={org.id} sourceName={org.name} />
								<code class="text-xs text-[var(--muted-foreground)]">{org.fhirBaseUrl || '(FhirBaseUrl not configured)'}</code>
								{#if org.isSandbox}<Badge variant="secondary">sandbox</Badge>{/if}
							</li>
						{/each}
					</ul>
				</div>
			</Card.Content>
		</Card.Root>
	</Tabs.Content>

	<Tabs.Content value="data">
		<EmptyState title="Export / Archive coming in a later update" description="Local SQLite data export (arkiv-style JSONL) will land in a future spec." />
	</Tabs.Content>

	<Tabs.Content value="about">
		<Card.Root>
			<Card.Header>
				<Card.Title>HealthAggregator</Card.Title>
				<Card.Description>Personal health record aggregator with local-first storage</Card.Description>
			</Card.Header>
			<Card.Content>
				<dl class="space-y-2 text-sm">
					<div class="flex gap-2">
						<dt class="text-[var(--muted-foreground)] w-32">Stack:</dt>
						<dd>.NET 10 Web API + SvelteKit 2 + SQLite (EF Core)</dd>
					</div>
					<div class="flex gap-2">
						<dt class="text-[var(--muted-foreground)] w-32">Integration:</dt>
						<dd>Epic SMART on FHIR (OAuth 2.0 + PKCE) with multi-org support</dd>
					</div>
				</dl>
			</Card.Content>
		</Card.Root>
	</Tabs.Content>
</Tabs.Root>
