import { goto } from '$app/navigation';

export function updateFilters(patch: Record<string, string | number | boolean | null | undefined>) {
	const url = new URL(window.location.href);
	for (const [k, v] of Object.entries(patch)) {
		if (v === null || v === undefined || v === '' || v === false) url.searchParams.delete(k);
		else url.searchParams.set(k, String(v));
	}
	goto(url, { replaceState: true, keepFocus: true, noScroll: true });
}
