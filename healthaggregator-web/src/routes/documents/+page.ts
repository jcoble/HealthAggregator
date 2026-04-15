import type { PageLoad } from './$types';
import { api, type RecordQuery } from '$lib/api';

export const load: PageLoad = async ({ fetch, url }) => {
	const params: RecordQuery = {
		search: url.searchParams.get('search') ?? undefined,
		status: url.searchParams.get('status') ?? undefined,
		from:   url.searchParams.get('from')   ?? undefined,
		to:     url.searchParams.get('to')     ?? undefined,
		source: url.searchParams.get('source') ?? undefined
	};
	const [rows, orgsResponse] = await Promise.all([
		api.getDocuments(fetch, params),
		api.getOrganizations(fetch)
	]);
	return { rows, sources: orgsResponse.organizations, params };
};
