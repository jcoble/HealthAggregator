import type { PageLoad } from './$types';
import { api, type EpicOrganization, type EpicConnection, type LabObservation, type TimelineItem } from '$lib/api';

const ABNORMAL_PREFIXES = ['HH', 'LL', 'H', 'L', 'A'];
function isAbnormal(lab: LabObservation): boolean {
	const interp = lab.interpretation?.toUpperCase() ?? '';
	if (interp && ABNORMAL_PREFIXES.some(p => interp.startsWith(p))) return true;
	if (lab.numericValue != null) {
		if (lab.referenceLow != null && lab.numericValue < lab.referenceLow) return true;
		if (lab.referenceHigh != null && lab.numericValue > lab.referenceHigh) return true;
	}
	return false;
}

export const load: PageLoad = async ({ fetch }) => {
	const [orgsResponse, connections, allLabs, timeline] = await Promise.all([
		api.getOrganizations(fetch),
		api.getConnections(fetch),
		api.getLabs(fetch),
		api.getTimeline(fetch, { take: 300 })
	]);

	const orgs: EpicOrganization[] = orgsResponse.organizations;
	const conns: EpicConnection[] = connections;

	const labCount = allLabs.length;
	const abnormalCount = allLabs.filter(isAbnormal).length;
	const timelineCount = timeline.length;
	const connectedCount = conns.filter(c => c.hasToken).length;
	const sourcesConnected = `${connectedCount} / ${orgs.length}`;

	const recentLabs: LabObservation[] = allLabs.slice(0, 8);
	const recentTimeline: TimelineItem[] = timeline.slice(0, 10);

	return {
		configured: orgsResponse.configured,
		orgs,
		connections: conns,
		stats: {
			labCount,
			abnormalCount,
			timelineCount,
			sourcesConnected
		},
		recentLabs,
		recentTimeline
	};
};
