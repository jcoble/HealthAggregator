import type { PageLoad } from './$types';
import { api, type TimelineQuery } from '$lib/api';

export const load: PageLoad = async ({ fetch, url }) => {
	const params: TimelineQuery = {
		kind:   url.searchParams.get('kind')   ?? undefined,
		source: url.searchParams.get('source') ?? undefined,
		from:   url.searchParams.get('from')   ?? undefined,
		to:     url.searchParams.get('to')     ?? undefined,
		take:   Number(url.searchParams.get('take') ?? 300)
	};
	const [items, orgsResponse] = await Promise.all([
		api.getTimeline(fetch, params),
		api.getOrganizations(fetch)
	]);
	return { items, sources: orgsResponse.organizations, params };
};
