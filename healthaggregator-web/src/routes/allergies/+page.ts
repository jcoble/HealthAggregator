import type { PageLoad } from './$types';
import { api, type RecordQuery } from '$lib/api';

export const load: PageLoad = async ({ fetch, url }) => {
	const params: RecordQuery = {
		search: url.searchParams.get('search') ?? undefined,
		status: url.searchParams.get('status') ?? undefined,
		source: url.searchParams.get('source') ?? undefined
	};
	const [rows, orgsResponse] = await Promise.all([
		api.getAllergies(fetch, params),
		api.getOrganizations(fetch)
	]);
	return { rows, sources: orgsResponse.organizations, params };
};
