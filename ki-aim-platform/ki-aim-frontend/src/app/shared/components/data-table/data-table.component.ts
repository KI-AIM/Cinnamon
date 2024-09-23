import {Component, Input, ViewChild} from "@angular/core";
import { TransformationService } from "../../services/transformation.service";
import { DataSet } from "src/app/shared/model/data-set";
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { TransformationResult } from "src/app/shared/model/transformation-result";
import { List } from "src/app/core/utils/list";
import { DataRowTransformationError } from "src/app/shared/model/data-row-transformation-error";
import {catchError, map, of, startWith, switchMap} from "rxjs";
import {DataConfigurationService} from "../../services/data-configuration.service";
import {DataConfiguration} from "../../model/data-configuration";
import {HttpClient} from "@angular/common/http";
import {environments} from "../../../../environments/environment";

@Component({
	selector: "app-data-table",
	templateUrl: "./data-table.component.html",
	styleUrls: ["./data-table.component.less"],
})
export class DataTableComponent {

    @Input() public step!: string;

	dataSource = new MatTableDataSource<TableElement>();
	@ViewChild(MatPaginator) paginator: MatPaginator;
	displayedColumns: string[] = ['position'];
    protected rowIndexOffset: number = 0;
	filterCriteria = "ALL";

    protected isLoading: boolean = false;
    protected total: number;

	constructor(
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly http: HttpClient,
		public transformationService: TransformationService,
	) {
	}

	ngAfterViewInit() {
		this.dataSource.paginator = this.paginator;

        if (this.step === 'validation') {
            this.transformationService.fetchTransformationResult().subscribe({
                next: transformationResult => {
                    this.displayedColumns = this.displayedColumns.concat(this.getColumnNames(transformationResult.dataSet.dataConfiguration))

                    this.dataSource.data = this.addColumnErrorsToTableData(
                        this.transformDataSet(
                            this.readdTransformationErrors(
                                this.removeRowsWithErrorsFromDataSet(transformationResult),
                                transformationResult.transformationErrors
                            )
                        ),
                        transformationResult.transformationErrors
                    );

                }, error: error => {
                    console.log(error());
                }
            });
        } else {
            this.dataConfigurationService.downloadDataConfigurationAsJson().subscribe(
                {
                    next: dataConfiguration => {
                        this.displayedColumns = this.displayedColumns.concat(this.getColumnNames(dataConfiguration));

                        this.paginator.page.pipe(
                            startWith({}),
                            switchMap(() => {
                                this.isLoading = true;
                                return this.http.get<DataSetPage>(environments.apiUrl + "/api/data/" + this.step + "/dataTable", {
                                    params: {
                                        page: this.paginator.pageIndex + 1,
                                        perPage: this.paginator.pageSize
                                    }
                                }).pipe(catchError(() => of(null)));
                            }),
                            map(value => {
                               if (value == null) {
                                   return [];
                               }
                               this.rowIndexOffset = (value.page - 1) * value.perPage;
                               this.isLoading = false;
                               this.total = value.total;
                               return value.data;
                            }),
                        ).subscribe({
                            next: value => {
                                const dataSet = new DataSet()
                                dataSet.data = value;
                                dataSet.dataConfiguration = dataConfiguration;
                                this.dataSource = new MatTableDataSource<TableElement>(this.transformDataSet(dataSet));
                            }
                        });
                    }
                }
            );
        }
	}

	/**
	 * Function that transforms a dataSet object
	 * into a format that is usable by the angular
	 * material data tables
	 * @param dataSet to be transformed
	 * @returns Array<TableElement>
	 */
	transformDataSet(dataSet: DataSet): TableElement[] {
		const transformedData: TableElement[] = [];

		dataSet.data.forEach((dataRow, index) => {
			const transformedRow: TableElement = {position: index, errorsInRow: []};
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
	 * Function that readds the rows that were faulty upon transformation
	 * to be displayed in the validation table
	 * @param dataSet: The data set to which the lines should be readded to
	 * @param transformationErrors: The transformation object that contains the error information
	 * @returns new DataSet
	 */
	readdTransformationErrors(dataSet: DataSet, transformationErrors: DataRowTransformationError[]): DataSet {

		var newDataSet = new DataSet();
		var dataArray = new Array<Array<any>>;

        if (transformationErrors.length === 0) {
            // All rows are valid
            dataSet.data.forEach((dataRow) => {
                dataArray.push(dataRow);
            });
        } else if (dataSet.data.length === 0) {
            // All rows are invalid
            transformationErrors.forEach(error => {
                dataArray.push(error.rawValues);
            });
        } else {
            let rowIndex = 0;
            let dataSetRowIndex = 0;

            transformationErrors.forEach(transformationError => {
                // Fill rows until the index of the transformation error is reached
                while (rowIndex < transformationError.index) {
                    dataArray.push(dataSet.data[dataSetRowIndex]);
                    rowIndex += 1;
                    dataSetRowIndex += 1;
                }
                // Add the transformation error
                dataArray.push(transformationError.rawValues);
                rowIndex += 1;
            });

            // Add valid rows after the last transformation error
            while (dataSetRowIndex < dataSet.data.length) {
                dataArray.push(dataSet.data[dataSetRowIndex]);
                dataSetRowIndex += 1;
            }
        }

		newDataSet.data = dataArray;
		newDataSet.dataConfiguration = dataSet.dataConfiguration;

		return newDataSet;
	}

	removeRowsWithErrorsFromDataSet(transformationResult: TransformationResult): DataSet {
		var dataSet = transformationResult.dataSet;

		var newDataSet = new DataSet();
		var dataArray = new Array<Array<any>>;
		var errorIndices = new List<number>();

		transformationResult.transformationErrors.forEach(error => {
			if (!errorIndices.contains(rowCounter)) {
				errorIndices.add(error.index);
			}
		});

		var rowCounter = 0;
		dataSet.data.forEach((dataRow) => {
			if (!errorIndices.contains(rowCounter)) {
				dataArray.push(dataRow);
			}
			rowCounter++;
		});
		newDataSet.data = dataArray;
		newDataSet.dataConfiguration = dataSet.dataConfiguration;

		return newDataSet;
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
		var resultIndices: Array<number>  = [];

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
	 * Returns the column names of a DataSet in a
	 * String Array
	 * @param dataSet to be processed
	 * @returns Array<string>
	 */
	getColumnNames(dataConfiguration: DataConfiguration): string[] {
		var result: string[] = [];

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
	 * Applies a filter to the table based on the select menu in frontend
	 * Shows different data depending on the number of errors in a row.
	 * @param $event the selectionChange event
	 */
	applyFilter($event: any) {
		this.filterCriteria = $event.value;
		switch (this.filterCriteria) {
			case "ALL": {
				this.dataSource.filterPredicate = (data: TableElement) => data.errorsInRow.length > 0 || data.errorsInRow.length <= 0;
				this.dataSource.filter = this.filterCriteria;
				break;
			}
			case "VALID": {
				this.dataSource.filterPredicate = (data: TableElement) => data.errorsInRow.length == 0;
				this.dataSource.filter = this.filterCriteria;
				break;
			}
			case "ERRORS": {
				this.dataSource.filterPredicate = (data: TableElement) => data.errorsInRow.length > 0;
				this.dataSource.filter = this.filterCriteria;
				break;
			}
		}
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
    page: number;
    perPage: number;
    total: number;
    totalPages: number;
}
