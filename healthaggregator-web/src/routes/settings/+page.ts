import type { PageLoad } from './$types';
import { api } from '$lib/api';

export const load: PageLoad = async ({ fetch }) => {
	const orgs = await api.getOrganizations(fetch);
	return { orgs };
};
