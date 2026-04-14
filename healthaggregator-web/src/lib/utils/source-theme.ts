const LOCKED: Record<string, string> = {
	'cleveland-clinic': 'red',
	'summa-health':     'blue',
	'epic-sandbox':     'grey',
	'manual-upload':    'amber',
};
const FALLBACK_PALETTE = ['purple', 'teal', 'pink', 'orange', 'cyan', 'indigo'];

export function sourceColor(sourceSystem: string): string {
	if (LOCKED[sourceSystem]) return LOCKED[sourceSystem];
	const hash = [...sourceSystem].reduce((a, c) => a + c.charCodeAt(0), 0);
	return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.length];
}

/** Tailwind class for a tinted SourceBadge pill. */
export function sourceBadgeClasses(sourceSystem: string): string {
	const color = sourceColor(sourceSystem);
	const map: Record<string, string> = {
		red:    'bg-red-500/15 text-red-300 border-red-500/30',
		blue:   'bg-blue-500/15 text-blue-300 border-blue-500/30',
		grey:   'bg-zinc-500/15 text-zinc-300 border-zinc-500/30',
		amber:  'bg-amber-500/15 text-amber-300 border-amber-500/30',
		purple: 'bg-purple-500/15 text-purple-300 border-purple-500/30',
		teal:   'bg-teal-500/15 text-teal-300 border-teal-500/30',
		pink:   'bg-pink-500/15 text-pink-300 border-pink-500/30',
		orange: 'bg-orange-500/15 text-orange-300 border-orange-500/30',
		cyan:   'bg-cyan-500/15 text-cyan-300 border-cyan-500/30',
		indigo: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
	};
	return map[color] ?? map.grey;
}
