<script lang="ts">
	import type { ImportJob } from '$lib/api';
	import type { Column } from '$lib/components/data-table/types';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import DataTable from '$lib/components/data-table/DataTable.svelte';
	import * as Card from '$lib/components/ui/card';
	import * as Tooltip from '$lib/components/ui/tooltip';
	import * as Table from '$lib/components/ui/table';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { api } from '$lib/api';
	import { invalidate, invalidateAll } from '$app/navigation';
	import { toast } from 'svelte-sonner';
	import { formatDateTime } from '$lib/utils/format';
	import { Upload, FileUp } from '@lucide/svelte';

	let { data } = $props();
	let fileInput: HTMLInputElement | undefined = $state();
	let uploading = $state(false);
	let lastSummary = $state<any | null>(null);

	async function onUpload(e: Event) {
		const input = e.target as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		uploading = true;
		try {
			const result: any = await api.uploadImport(file);
			lastSummary = result;
			toast.success(`Imported ${file.name}`);
			await invalidateAll();
			if (fileInput) fileInput.value = '';
		} catch (err) {
			toast.error(err instanceof Error ? err.message : 'Upload failed');
		} finally {
			uploading = false;
		}
	}

	const columns: Column<ImportJob>[] = [
		{ id: 'startedAt', header: 'Started', sortBy: r => new Date(r.startedAt) },
		{ id: 'file', header: 'File' },
		{ id: 'kind', header: 'Kind' },
		{ id: 'status', header: 'Status' },
		{ id: 'records', header: 'Records', hideOn: 'md' },
		{ id: 'labs', header: 'Labs', hideOn: 'md' }
	];

	function kindOfJob(job: ImportJob): { label: string; variant: 'default' | 'warning' } {
		if (job.sourceSystem === 'manual-upload' && job.labObservationsUpserted === 0 && job.sourceRecordsUpserted === 1) {
			return { label: 'stored-file-metadata', variant: 'warning' };
		}
		return { label: 'fhir-json', variant: 'default' };
	}
</script>

<PageHeader title="Imports" subtitle={`${data.imports.length} manual imports`}>
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="imports-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

<div class="grid gap-6 lg:grid-cols-2">
	<Card.Root>
		<Card.Header>
			<Card.Title>Upload a record</Card.Title>
			<Card.Description>FHIR JSON bundles are parsed into structured tables. Other formats (PDF, CSV, XLSX) are stored as file metadata only — structured parsing lands in Stream C.</Card.Description>
		</Card.Header>
		<Card.Content>
			<label
				for="import-file-input"
				class="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-[var(--border)] bg-[var(--muted)]/30 py-10 cursor-pointer hover:bg-[var(--muted)]/50 transition"
				data-testid="imports-upload-zone"
			>
				<FileUp size={28} class="text-[var(--muted-foreground)]" />
				<span class="text-sm">Drop a file or click to choose</span>
				<span class="text-xs text-[var(--muted-foreground)]">FHIR JSON, XML, PDF, CSV, XLSX</span>
				<input
					id="import-file-input"
					bind:this={fileInput}
					type="file"
					class="hidden"
					onchange={onUpload}
					disabled={uploading}
					data-testid="imports-upload-input"
				/>
			</label>
			{#if uploading}
				<div class="text-sm text-[var(--muted-foreground)] mt-3">Uploading…</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<Card.Root>
		<Card.Header>
			<Card.Title>History</Card.Title>
			<Card.Description>Recent imports across all sources</Card.Description>
		</Card.Header>
		<Card.Content class="p-0">
			{#if data.imports.length === 0}
				<div class="p-6">
					<EmptyState
						icon={Upload}
						title="No imports yet"
						description="Drop a FHIR bundle to populate your local database."
					/>
				</div>
			{:else}
				<DataTable {columns} data={data.imports} pageSize={50}>
					{#snippet row(job)}
						{@const kind = kindOfJob(job)}
						<Table.Cell>{formatDateTime(job.startedAt)}</Table.Cell>
						<Table.Cell class="truncate max-w-[200px]">{job.sourceName}</Table.Cell>
						<Table.Cell>
							{#if kind.variant === 'warning'}
								<Tooltip.Provider>
									<Tooltip.Root>
										<Tooltip.Trigger>
											<Badge variant="warning">{kind.label}</Badge>
										</Tooltip.Trigger>
										<Tooltip.Content>Stored as metadata only — structured parsing for this format isn't wired yet</Tooltip.Content>
									</Tooltip.Root>
								</Tooltip.Provider>
							{:else}
								<Badge>{kind.label}</Badge>
							{/if}
						</Table.Cell>
						<Table.Cell>
							{#if job.status === 'completed' || job.completedAt}
								<Badge variant="success">completed</Badge>
							{:else if job.error}
								<Badge variant="destructive">failed</Badge>
							{:else}
								<Badge variant="secondary">{job.status}</Badge>
							{/if}
						</Table.Cell>
						<Table.Cell class="hidden md:table-cell">{job.sourceRecordsUpserted}</Table.Cell>
						<Table.Cell class="hidden md:table-cell">{job.labObservationsUpserted}</Table.Cell>
					{/snippet}
				</DataTable>
			{/if}
		</Card.Content>
	</Card.Root>
</div>

{#if lastSummary?.summary}
	<Card.Root class="mt-6" data-testid="last-upload-summary">
		<Card.Header>
			<Card.Title>Last upload summary</Card.Title>
		</Card.Header>
		<Card.Content>
			<div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
				<div><span class="text-[var(--muted-foreground)]">Source records</span><div class="font-semibold">{lastSummary.summary.sourceRecordsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Labs</span><div class="font-semibold">{lastSummary.summary.labObservationsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Reports</span><div class="font-semibold">{lastSummary.summary.diagnosticReportsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Medications</span><div class="font-semibold">{lastSummary.summary.medicationsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Conditions</span><div class="font-semibold">{lastSummary.summary.conditionsUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Allergies</span><div class="font-semibold">{lastSummary.summary.allergiesUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Encounters</span><div class="font-semibold">{lastSummary.summary.encountersUpserted ?? 0}</div></div>
				<div><span class="text-[var(--muted-foreground)]">Documents</span><div class="font-semibold">{lastSummary.summary.documentsUpserted ?? 0}</div></div>
			</div>
		</Card.Content>
	</Card.Root>
{/if}
