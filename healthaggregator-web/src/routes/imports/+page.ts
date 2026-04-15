import type { PageLoad } from './$types';
import { api } from '$lib/api';

export const load: PageLoad = async ({ fetch }) => {
	const imports = await api.getImports(fetch);
	return { imports };
};
