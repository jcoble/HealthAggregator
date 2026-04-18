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
				<div>
					<div class="text-sm text-[var(--muted-foreground)] mb-2">Client IDs by environment:</div>
					<ul class="space-y-1.5 text-sm" data-testid="settings-epic-client-ids">
						<li class="flex items-center gap-2">
							<span class="w-48 text-[var(--muted-foreground)]">NonProductionClientId</span>
							{#if data.orgs.clientIds.nonProduction}
								<Badge variant="success" data-testid="settings-clientid-nonprod-set">Set</Badge>
							{:else}
								<Badge variant="warning" data-testid="settings-clientid-nonprod-unset">Not set</Badge>
							{/if}
							<span class="text-xs text-[var(--muted-foreground)]">Epic sandbox + customer non-prod</span>
						</li>
						<li class="flex items-center gap-2">
							<span class="w-48 text-[var(--muted-foreground)]">ProductionClientId</span>
							{#if data.orgs.clientIds.production}
								<Badge variant="success" data-testid="settings-clientid-prod-set">Set</Badge>
							{:else}
								<Badge variant="warning" data-testid="settings-clientid-prod-unset">Not set</Badge>
							{/if}
							<span class="text-xs text-[var(--muted-foreground)]">Customer production envs</span>
						</li>
						<li class="flex items-center gap-2">
							<span class="w-48 text-[var(--muted-foreground)]">ClientId (legacy fallback)</span>
							{#if data.orgs.clientIds.legacy}
								<Badge variant="secondary" data-testid="settings-clientid-legacy-set">Set</Badge>
							{:else}
								<Badge variant="outline" data-testid="settings-clientid-legacy-unset">Not set</Badge>
							{/if}
							<span class="text-xs text-[var(--muted-foreground)]">Used when env-specific id is missing</span>
						</li>
					</ul>
				</div>
				<div class="text-sm text-[var(--muted-foreground)] space-y-1">
					<div>Set via user-secrets — each is independent:</div>
					<pre class="bg-[var(--muted)] px-2 py-2 rounded text-xs overflow-x-auto"><code>dotnet user-secrets set "Epic:NonProductionClientId" "&lt;non-prod-id&gt;" --project HealthAggregator.Api
dotnet user-secrets set "Epic:ProductionClientId"    "&lt;prod-id&gt;"     --project HealthAggregator.Api
dotnet user-secrets set "Epic:ClientId"              "&lt;fallback-id&gt;" --project HealthAggregator.Api</code></pre>
				</div>
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
