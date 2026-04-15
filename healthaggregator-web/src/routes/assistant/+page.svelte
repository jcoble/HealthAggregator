<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { api, type AssistantReply } from '$lib/api';
	import { toast } from 'svelte-sonner';
	import { formatDate } from '$lib/utils/format';
	import { Bot, Send } from '@lucide/svelte';

	type Message = { role: 'user' | 'assistant'; content: string; reply?: AssistantReply };

	let messages = $state<Message[]>([]);
	let input = $state('');
	let sending = $state(false);

	const SUGGESTIONS = [
		'Show my recent labs',
		"What's abnormal?",
		'HbA1c trend',
		'Medications from Cleveland Clinic'
	];

	async function send(text?: string) {
		const content = (text ?? input).trim();
		if (!content || sending) return;
		input = '';
		sending = true;
		messages = [...messages, { role: 'user', content }];
		try {
			const reply = await api.askAssistant(content);
			messages = [...messages, { role: 'assistant', content: reply.message, reply }];
		} catch (e) {
			toast.error(e instanceof Error ? e.message : 'Assistant failed');
		} finally {
			sending = false;
		}
	}

	function newConversation() {
		messages = [];
		input = '';
	}

	function onKey(e: KeyboardEvent) {
		if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
			e.preventDefault();
			send();
		}
	}
</script>

<PageHeader title="Assistant" subtitle="Ask about your labs (read-only pattern matcher today; LLM-backed in Stream D)">
	{#snippet actions()}
		{#if messages.length > 0}
			<Button variant="secondary" onclick={newConversation} data-testid="assistant-new-conversation">New conversation</Button>
		{/if}
	{/snippet}
</PageHeader>

<div class="grid grid-rows-[1fr_auto] gap-4 h-[calc(100vh-11rem)]">
	<div class="overflow-y-auto pr-2" data-testid="assistant-messages">
		{#if messages.length === 0}
			<div class="flex flex-col items-center justify-center h-full text-center gap-6">
				<div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-[var(--primary)]/15 text-[var(--primary)]">
					<Bot size={28} />
				</div>
				<div>
					<div class="text-lg font-semibold">Ask about your records</div>
					<div class="text-sm text-[var(--muted-foreground)] mt-1 max-w-md">
						The assistant searches your labs and returns cited results. Try one of these:
					</div>
				</div>
				<div class="flex flex-wrap justify-center gap-2 max-w-xl">
					{#each SUGGESTIONS as s, i (i)}
						<Button
							variant="outline"
							size="sm"
							onclick={() => send(s)}
							data-testid={`assistant-suggestion-${i}`}
						>{s}</Button>
					{/each}
				</div>
			</div>
		{:else}
			<div class="space-y-5">
				{#each messages as m, i (i)}
					{#if m.role === 'user'}
						<div class="flex justify-end" data-testid={`assistant-message-user-${i}`}>
							<div class="max-w-2xl rounded-2xl bg-[var(--primary)] text-[var(--primary-foreground)] px-4 py-2.5">
								{m.content}
							</div>
						</div>
					{:else}
						<div data-testid={`assistant-message-assistant-${i}`}>
							<Card.Root>
								<Card.Content class="pt-4">
									<div class="whitespace-pre-wrap text-sm">{m.content}</div>
									{#if m.reply && m.reply.citations.length > 0}
										<div class="mt-4 space-y-1.5">
											<div class="text-xs uppercase tracking-wide text-[var(--muted-foreground)]">Citations</div>
											{#each m.reply.citations as c, j (c.fhirReference + j)}
												<div
													class="flex items-center gap-2 text-xs text-[var(--muted-foreground)] rounded px-2 py-1 bg-[var(--muted)]/40"
													data-testid={`assistant-citation-${i}-${j}`}
												>
													<span class="font-medium text-[var(--foreground)]">{c.testName}</span>
													<Badge variant="secondary">{c.value}{c.unit ? ` ${c.unit}` : ''}</Badge>
													<span>{formatDate(c.effectiveAt)}</span>
													<SourceBadge sourceSystem={c.sourceName.toLowerCase().replace(/\s+/g, '-')} sourceName={c.sourceName} />
												</div>
											{/each}
										</div>
									{/if}
								</Card.Content>
							</Card.Root>
						</div>
					{/if}
				{/each}
			</div>
		{/if}
	</div>

	<div class="flex gap-2">
		<textarea
			bind:value={input}
			onkeydown={onKey}
			placeholder="Ask about your records... (⌘⏎ to send)"
			rows="2"
			disabled={sending}
			data-testid="assistant-input"
			class="flex-1 resize-none rounded-md border border-[var(--border)] bg-[var(--card)] text-[var(--foreground)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--primary)]"
		></textarea>
		<Button onclick={() => send()} disabled={!input.trim() || sending} data-testid="assistant-submit">
			<Send size={16} />
			{sending ? 'Thinking…' : 'Ask'}
		</Button>
	</div>
</div>
