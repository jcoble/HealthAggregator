import type { Snippet } from 'svelte';

export type Column<T> = {
	id: string;
	header: string;
	/** When set, the column is hidden at/below that Tailwind breakpoint. */
	hideOn?: 'sm' | 'md' | 'lg';
	/** Render function for the cell. Gets the row. */
	cell: Snippet<[T]>;
	/** Optional accessor for sorting purposes. If omitted, column is not sortable. */
	sortBy?: (row: T) => string | number | Date | null | undefined;
	widthClass?: string;
};

export type DataTableProps<T> = {
	columns: Column<T>[];
	data: T[];
	pageSize?: number;
	loading?: boolean;
	empty?: Snippet;
};
