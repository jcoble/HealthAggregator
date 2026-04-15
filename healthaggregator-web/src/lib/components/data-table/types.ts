import type { Snippet } from 'svelte';

export type Column<T> = {
	id: string;
	header: string;
	/** When set, the column is hidden at/below that Tailwind breakpoint. */
	hideOn?: 'sm' | 'md' | 'lg';
	/** Optional accessor for sorting. If omitted, column is not sortable. */
	sortBy?: (row: T) => string | number | Date | null | undefined;
	widthClass?: string;
};

export type DataTableProps<T> = {
	columns: Column<T>[];
	data: T[];
	/** Renders all cells for a single row. Must emit one Table.Cell per column, in order. */
	row: Snippet<[T]>;
	pageSize?: number;
	loading?: boolean;
	empty?: Snippet;
};
