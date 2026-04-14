export type EmptyStateProps = {
	title: string;
	description: string;
	actionLabel?: string;
	actionHref?: string;
};

/**
 * Returns props for the EmptyState component that branch on why the list is empty.
 * entityName: singular, lower-case — e.g. "medication", "lab".
 */
export function getEmptyStateProps(
	entityName: string,
	rowCount: number,
	filtersActive: boolean
): EmptyStateProps {
	if (rowCount > 0) throw new Error('getEmptyStateProps called with non-empty list');
	if (filtersActive) {
		return {
			title: `No ${entityName} match your filters`,
			description: 'Try widening the date range or clearing a filter.',
			actionLabel: 'Clear filters',
			actionHref: '?'
		};
	}
	return {
		title: `No ${entityName} yet`,
		description: 'Sync your Epic connections to pull records.',
		actionLabel: 'Manage Connections',
		actionHref: '/connections'
	};
}
