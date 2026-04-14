<script lang="ts">
	import { cn } from '$lib/utils';
	import { AlertTriangle, AlertCircle, Info, X } from '@lucide/svelte';
	import type { Snippet } from 'svelte';

	let { variant = 'warning', title, dismissible = false, onDismiss, children, action }: {
		variant?: 'warning' | 'destructive' | 'info';
		title: string;
		dismissible?: boolean;
		onDismiss?: () => void;
		children?: Snippet;
		action?: Snippet;
	} = $props();

	const ICONS = { warning: AlertTriangle, destructive: AlertCircle, info: Info };
	const Icon = $derived(ICONS[variant]);

	const variantClasses = {
		warning:     'bg-[var(--warning)]/10 border-[var(--warning)]/30 text-amber-200',
		destructive: 'bg-[var(--destructive)]/10 border-[var(--destructive)]/30 text-red-200',
		info:        'bg-[var(--primary)]/10 border-[var(--primary)]/30 text-blue-200',
	};
</script>

<div
	data-testid={`error-banner-${variant}`}
	class={cn('flex items-start gap-3 rounded-lg border px-4 py-3', variantClasses[variant])}
>
	<Icon size={18} class="mt-0.5 shrink-0" />
	<div class="flex-1 min-w-0">
		<div class="font-medium">{title}</div>
		{#if children}
			<div class="text-sm mt-1 opacity-90">{@render children()}</div>
		{/if}
	</div>
	{#if action}
		<div class="shrink-0">{@render action()}</div>
	{/if}
	{#if dismissible}
		<button
			type="button"
			onclick={onDismiss}
			data-testid="error-banner-dismiss"
			class="shrink-0 opacity-70 hover:opacity-100"
			aria-label="Dismiss"
		><X size={16} /></button>
	{/if}
</div>
