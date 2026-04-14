<script lang="ts">
	import NavItem from './NavItem.svelte';
	import NavGroup from './NavGroup.svelte';
	import TopBar from './TopBar.svelte';
	import {
		LayoutDashboard, FolderHeart, Activity, HeartPulse, Pill, Stethoscope, ShieldAlert,
		CalendarClock, FileText, Sparkles, History, Bot, Plug, Link as LinkIcon,
		Upload, Settings, Menu, X
	} from '@lucide/svelte';
	import { onMount } from 'svelte';
	import type { Snippet } from 'svelte';

	let { children, actions }: { children: Snippet; actions?: Snippet } = $props();

	let mobileOpen = $state(false);
	let isMobile = $state(false);

	onMount(() => {
		const mq = window.matchMedia('(max-width: 767px)');
		const handler = () => (isMobile = mq.matches);
		handler();
		mq.addEventListener('change', handler);
		return () => mq.removeEventListener('change', handler);
	});
</script>

<div data-testid="app-shell" class="min-h-screen bg-[var(--background)] md:grid md:grid-cols-[260px_minmax(0,1fr)]">
	<aside
		data-testid="sidebar"
		class={`flex flex-col border-r border-[var(--border)] bg-[#101114] ${isMobile && !mobileOpen ? 'hidden' : ''} ${isMobile ? 'fixed inset-y-0 left-0 z-40 w-72' : 'sticky top-0 h-screen'}`}
	>
		<div class="flex items-center justify-between p-5">
			<a href="/" data-testid="brand-link" class="flex items-center gap-3">
				<div class="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--primary)]/15 text-[var(--primary)]">
					<HeartPulse size={19} />
				</div>
				<div class="min-w-0">
					<div class="font-semibold text-[var(--foreground)]">HealthAggregator</div>
					<div class="text-xs text-[var(--muted-foreground)]">Local-first records</div>
				</div>
			</a>
			{#if isMobile}
				<button
					type="button"
					onclick={() => (mobileOpen = false)}
					data-testid="sidebar-close"
					class="text-[var(--muted-foreground)]"
					aria-label="Close menu"
				><X size={18} /></button>
			{/if}
		</div>

		<nav class="flex-1 overflow-y-auto px-3 pb-4" aria-label="Primary">
			<NavItem href="/" icon={LayoutDashboard} label="Dashboard" />

			<NavGroup id="records" icon={FolderHeart} title="Records">
				<NavItem href="/labs" icon={Activity} label="Labs" />
				<NavItem href="/vitals" icon={HeartPulse} label="Vitals" />
				<NavItem href="/medications" icon={Pill} label="Medications" />
				<NavItem href="/conditions" icon={Stethoscope} label="Conditions" />
				<NavItem href="/allergies" icon={ShieldAlert} label="Allergies" />
				<NavItem href="/encounters" icon={CalendarClock} label="Encounters" />
				<NavItem href="/documents" icon={FileText} label="Documents" />
			</NavGroup>

			<NavGroup id="insights" icon={Sparkles} title="Insights">
				<NavItem href="/timeline" icon={History} label="Timeline" />
				<NavItem href="/assistant" icon={Bot} label="Assistant" />
			</NavGroup>

			<NavGroup id="sources" icon={Plug} title="Data Sources">
				<NavItem href="/connections" icon={LinkIcon} label="Connections" />
				<NavItem href="/imports" icon={Upload} label="Imports" />
			</NavGroup>
		</nav>

		<div class="border-t border-[var(--border)] px-3 py-3">
			<NavItem href="/settings" icon={Settings} label="Settings" />
		</div>
	</aside>

	{#if isMobile && mobileOpen}
		<button
			type="button"
			aria-label="Close menu"
			onclick={() => (mobileOpen = false)}
			data-testid="sidebar-backdrop"
			class="fixed inset-0 z-30 bg-black/50"
		></button>
	{/if}

	<div class="flex min-h-screen flex-col">
		<TopBar>
			{#snippet actions()}
				{#if isMobile}
					<button
						type="button"
						onclick={() => (mobileOpen = !mobileOpen)}
						data-testid="sidebar-open"
						class="mr-2 text-[var(--muted-foreground)]"
						aria-label="Open menu"
					><Menu size={18} /></button>
				{/if}
				{#if actions}{@render actions()}{/if}
			{/snippet}
		</TopBar>
		<main data-testid="content" class="flex-1 px-6 py-6">
			{@render children()}
		</main>
	</div>
</div>
