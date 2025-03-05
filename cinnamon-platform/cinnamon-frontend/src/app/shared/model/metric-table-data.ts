import { MetricImportance } from "./project-settings";

export type SortDirection = 'asc' | 'desc' | 'original';
export type SortType = 'metric' | 'colorIndex' | 'absolute' | 'percentage';

export class MetricTableFilterData {
    filterText: string = "";
    importance: MetricImportance | null = null;
}

export class MetricTableSortData {
    direction: SortDirection | null = null;
    column: SortType | null = null;
}

export class MetricTableData {
    filter: MetricTableFilterData = new MetricTableFilterData();
    sort: MetricTableSortData = new MetricTableSortData();
}
