import type { PageLoad } from './$types';
import { api, type LabsQuery } from '$lib/api';

export const load: PageLoad = async ({ fetch, url }) => {
	const params: LabsQuery = {
		search:   url.searchParams.get('search')   ?? undefined,
		from:     url.searchParams.get('from')     ?? undefined,
		to:       url.searchParams.get('to')       ?? undefined,
		loinc:    url.searchParams.get('loinc')    ?? undefined,
		source:   url.searchParams.get('source')   ?? undefined,
		abnormal: url.searchParams.get('abnormal') === 'true' ? true : undefined
	};
	const [labs, series, orgsResponse] = await Promise.all([
		api.getLabs(fetch, params),
		api.getLabSeries(fetch, { loinc: params.loinc, source: params.source }),
		api.getOrganizations(fetch)
	]);
	return { labs, series, sources: orgsResponse.organizations, params };
};
