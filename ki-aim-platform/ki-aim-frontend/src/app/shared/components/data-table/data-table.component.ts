import { AfterViewInit, Component, Input, OnInit, ViewChild } from "@angular/core";
import { DataSet } from "src/app/shared/model/data-set";
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { DataRowTransformationError } from "src/app/shared/model/data-row-transformation-error";
import { catchError, map, Observable, of, startWith, switchMap } from "rxjs";
import {DataConfigurationService} from "../../services/data-configuration.service";
import {DataConfiguration} from "../../model/data-configuration";
import {HttpClient} from "@angular/common/http";
import {environments} from "../../../../environments/environment";
import { DataSetInfo } from "../../model/data-set-info";
import { DataSetInfoService } from "../../../features/data-upload/services/data-set-info.service";

@Component({
	selector: "app-data-table",
	templateUrl: "./data-table.component.html",
	styleUrls: ["./data-table.component.less"],
})
export class DataTableComponent implements OnInit, AfterViewInit {
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public columnIndex: number | null = null;

	dataSource = new MatTableDataSource<TableElement>();
	@ViewChild(MatPaginator) paginator: MatPaginator;
	displayedColumns: string[] = ['position'];
    protected rowIndexOffset: number = 0;
	protected errorFilter = "ALL";
    protected holdOutFilter = "NOT_HOLD_OUT";

    protected isLoading: boolean = false;
    protected total: number;

    protected dataSetInfo$: Observable<DataSetInfo>;

	constructor(
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly dataSetInfoService: DataSetInfoService,
        private readonly http: HttpClient,
	) {
	}

    ngOnInit() {
        this.dataSetInfo$ = this.dataSetInfoService.getDataSetInfo(this.getSource());
    }

    ngAfterViewInit() {
		this.dataSource.paginator = this.paginator;

        this.dataConfigurationService.downloadDataConfigurationAsJson().subscribe(
            {
                next: (dataConfiguration: DataConfiguration) => {
                    let columnName = '';
                    if (this.columnIndex !== null) {
                        const columnConfiguration = dataConfiguration.configurations[this.columnIndex];
                        dataConfiguration = new DataConfiguration();
                        dataConfiguration.addColumnConfiguration(columnConfiguration);
                        columnName = columnConfiguration.name;
                    }

                    this.displayedColumns = this.displayedColumns.concat(this.getColumnNames(dataConfiguration));

                    this.paginator.page.pipe(
                        startWith({}),
                        switchMap(() => {
                            this.isLoading = true;
                            return this.http.get<DataSetPage>(environments.apiUrl + "/api/data/" + this.getSource() + "/transformationResult/page", {
                                params: {
                                    defaultNullEncoding: "$value",
                                    columns: columnName,
                                    holdOutSelector: this.holdOutFilter,
                                    page: this.paginator.pageIndex + 1,
                                    perPage: this.paginator.pageSize,
                                    rowSelector: this.errorFilter,
                                    source: this.getSourceType(),
                                }
                            }).pipe(catchError(() => of(null)));
                        }),
                        map(value => {
                            if (value == null) {
                                return null;
                            }
                            this.rowIndexOffset = (value.page - 1) * value.perPage;
                            this.isLoading = false;
                            this.total = value.total;
                            return value;
                        }),
                    ).subscribe({
                        next: value => {
                            const dataSet = new DataSet()
                            dataSet.data = value!.data;
                            dataSet.dataConfiguration = dataConfiguration;
                            this.dataSource = new MatTableDataSource<TableElement>(this.addColumnErrorsToTableData(this.transformDataSet(dataSet, value!.rowNumbers), value!.transformationErrors));
                        }
                    });
                }
            }
        );
	}

	/**
	 * Function that transforms a dataSet object
	 * into a format that is usable by the angular
	 * material data tables
	 * @param dataSet to be transformed
     * @param rowNumbers Mapping of row numbers
	 * @returns Array<TableElement>
	 */
	transformDataSet(dataSet: DataSet, rowNumbers: number[] | null) : TableElement[] {
		const transformedData: TableElement[] = [];

		dataSet.data.forEach((dataRow, index) => {
            const position = rowNumbers === null ? index : rowNumbers[index]
			const transformedRow: TableElement = {position: position, errorsInRow: []};
			dataRow.forEach((dataItem, index) => {
				const columnName =
					dataSet.dataConfiguration.configurations[index].name;
				transformedRow[columnName as string] = dataItem?.toString();
			});
			transformedData.push(transformedRow);
		});

		return transformedData;
	}

	/**
	 * Adds a new entry to the transformed TableElement Array
	 * that contains the indices of the faulty columns of each row
	 * so they can be highlighted in the frontend
	 * @param data transformed Array<TableElement>
	 * @param transformationErrors Array<DataRowTransformationError>
	 * @returns adjusted Array<TableElement>
	 */
	addColumnErrorsToTableData(data: TableElement[], transformationErrors: DataRowTransformationError[]): TableElement[] {
		data.forEach((dataRow, index) => {
			dataRow.errorsInRow = this.getErrorColumnIndicesForRowIndex(index, transformationErrors);
		})
		return data;
	}

	/**
	 * Processes the DataRowTransformationErrors and returns
	 * a list of indices if a column in a row has any errors.
	 * Returns an empty Array if no error is present
	 * @param index of the row
	 * @param transformationErrors Array of transformation Errors
	 * @returns Array with Indices
	 */
	getErrorColumnIndicesForRowIndex(index: number, transformationErrors: DataRowTransformationError[]): Array<number> {
		const resultIndices: Array<number>  = [];

		transformationErrors.forEach(transformationError => {
			if (transformationError.index === index) {
				transformationError.dataTransformationErrors.forEach(errors => {
					resultIndices.push(errors.index)
				});
			}
		});

		return resultIndices;
	}

	/**
	 * Returns the column names of a DataConfiguration in a String Array
	 * @param dataConfiguration to be processed
	 * @returns Array<string>
	 */
	getColumnNames(dataConfiguration: DataConfiguration): string[] {
		const result: string[] = [];

		dataConfiguration.configurations.forEach((column) => {
			result.push(column.name as string);
		});

		return result;
	}

	/**
	 * Returns true if the input error array has any entries.
	 * Returns false if the array is empty
	 * @param errorArray the Array stored in TableElement.errorsInRow
	 * @returns boolean
	 */
	rowHasErrors(errorArray: Array<any>): boolean {
		return errorArray.length > 0;
	}

	/**
	 * Applies a filter to the table based on the select menu in frontend.
	 * Shows different data depending on the number of errors in a row.
     * It triggers the table to fetch the data from the backend.
     *
	 * @param $event the selectionChange event
	 */
	applyErrorFilter($event: any) {
		this.errorFilter = $event.value;
        this.paginator.page.emit();
	}

    /**
     * Applies the hold-out split filter of the event.
     * It triggers the table to fetch the data from the backend.
     *
     * @param $event Event
     */
    applyHoldOutFilter($event: any) {
        this.holdOutFilter = $event.value;
        this.paginator.page.emit();
    }

    private getSource(): string {
        if (this.sourceProcess) {
            return this.sourceProcess;
        }
        if (this.sourceDataset) {
            return this.sourceDataset;
        }

        return 'VALIDATION';
    }

    private getSourceType(): string {
        if (this.sourceProcess) {
            return 'JOB';
        }
        if (this.sourceDataset) {
            return 'DATASET';
        }

        return 'DATASET';
    }
}

/**
 * Interface that dynamically stores the Value of a
 * row of a DataSet and assigns them to the column name.
 * This format is needed by the Material Table
 */
interface TableElement {
	position: number;
	[key: string]: any;
	errorsInRow: Array<any>;
}

interface DataSetPage {
    data: Array<Array<any>>;
    transformationErrors: DataRowTransformationError[];
    rowNumbers: number[] | null;

    page: number;
    perPage: number;
    total: number;
    totalPages: number;
}
