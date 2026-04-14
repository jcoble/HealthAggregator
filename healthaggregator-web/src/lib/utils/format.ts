export function formatDate(dateStr: string | null | undefined): string {
	if (!dateStr) return '—';
	const d = new Date(dateStr);
	if (isNaN(d.getTime())) return '—';
	return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(dateStr: string | null | undefined): string {
	if (!dateStr) return '—';
	const d = new Date(dateStr);
	if (isNaN(d.getTime())) return '—';
	return d.toLocaleString(undefined, {
		year: 'numeric', month: 'short', day: 'numeric',
		hour: 'numeric', minute: '2-digit'
	});
}

export function formatRelativeTime(dateStr: string | null | undefined): string {
	if (!dateStr) return '—';
	const d = new Date(dateStr);
	if (isNaN(d.getTime())) return '—';
	const diffMs = Date.now() - d.getTime();
	const minutes = Math.round(diffMs / 60000);
	if (minutes < 1) return 'just now';
	if (minutes < 60) return `${minutes}m ago`;
	const hours = Math.round(minutes / 60);
	if (hours < 24) return `${hours}h ago`;
	const days = Math.round(hours / 24);
	if (days < 30) return `${days}d ago`;
	return formatDate(dateStr);
}
