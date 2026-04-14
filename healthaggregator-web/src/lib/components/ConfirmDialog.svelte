<script lang="ts">
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { AlertTriangle } from '@lucide/svelte';

	type Props = {
		open: boolean;
		title?: string;
		description?: string;
		confirmLabel?: string;
		cancelLabel?: string;
		variant?: 'default' | 'destructive';
		confirmVariant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link';
		loading?: boolean;
		closable?: boolean;
		testId?: string;
		onconfirm: () => void;
		oncancel: () => void;
	};

	let {
		open = $bindable(false),
		title = 'Are you sure?',
		description = 'This action cannot be undone.',
		confirmLabel = 'Confirm',
		cancelLabel = 'Cancel',
		variant = 'default',
		confirmVariant,
		loading = false,
		closable = true,
		testId = 'confirm-dialog',
		onconfirm,
		oncancel
	}: Props = $props();

	function handleConfirm() {
		onconfirm();
	}

	function handleCancel() {
		oncancel();
		open = false;
	}
</script>

<Dialog.Root bind:open>
	<Dialog.Content
		class="sm:max-w-md"
		showCloseButton={closable}
		interactOutsideBehavior={closable ? 'close' : 'ignore'}
		escapeKeydownBehavior={closable ? 'close' : 'ignore'}
		data-testid={testId}
	>
		<Dialog.Header>
			<div class="flex items-center gap-3">
				{#if variant === 'destructive'}
					<div class="rounded-full bg-destructive/10 p-2">
						<AlertTriangle class="h-5 w-5 text-destructive" />
					</div>
				{/if}
				<Dialog.Title>{title}</Dialog.Title>
			</div>
			<Dialog.Description class="pt-2">
				{description}
			</Dialog.Description>
		</Dialog.Header>
		<Dialog.Footer class="gap-2 sm:gap-0">
			<Button variant="outline" onclick={handleCancel} disabled={loading} data-testid="{testId}-cancel">
				{cancelLabel}
			</Button>
			<Button
				variant={confirmVariant ?? (variant === 'destructive' ? 'destructive' : 'default')}
				onclick={handleConfirm}
				disabled={loading}
				data-testid="{testId}-confirm"
			>
				{#if loading}
					<span class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"></span>
				{/if}
				{confirmLabel}
			</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
