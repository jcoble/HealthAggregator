import type { PageLoad } from './$types';
import { api } from '$lib/api';

export const load: PageLoad = async ({ fetch }) => {
	const [orgsResponse, connections] = await Promise.all([
		api.getOrganizations(fetch),
		api.getConnections(fetch)
	]);
	return {
		configured: orgsResponse.configured,
		orgs: orgsResponse.organizations,
		connections
	};
};
