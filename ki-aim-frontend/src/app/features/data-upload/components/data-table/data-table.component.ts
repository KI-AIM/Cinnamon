import { Component, TemplateRef, ViewChild } from "@angular/core";
import { TransformationService } from "../../services/transformation.service";
import { DataSet } from "src/app/shared/model/data-set";
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { TransformationResult } from "src/app/shared/model/transformation-result";
import { List } from "src/app/core/utils/list";
import { DataRowTransformationError } from "src/app/shared/model/data-row-transformation-error";

@Component({
	selector: "app-data-table",
	templateUrl: "./data-table.component.html",
	styleUrls: ["./data-table.component.less"],
})
export class DataTableComponent {
	dataSource = new MatTableDataSource<TableElement>(
		this.addColumnErrorsToTableData(
			this.transformDataSet(
				this.readdTransformationErrors(
					this.transformationService.getTransformationResult()
				)
			), this.transformationService.getTransformationResult().transformationErrors
		)
	);
	@ViewChild(MatPaginator) paginator: MatPaginator;
	displayedColumns: string[] = ['position'].concat(this.getColumnNames(this.transformationService.getTransformationResult().dataSet));
	filterCriteria = "ALL"; 
	
	
	constructor(
		public transformationService: TransformationService,
	) {
	}

	ngAfterViewInit() {
		this.dataSource.paginator = this.paginator;
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
				transformedRow[columnName as string] = dataItem.toString();
			});
			transformedData.push(transformedRow);
		});

		return transformedData;
	}

	/**
	 * Function that readds the rows that were faulty upon transformation 
	 * to be displayed in the validation table
	 * @param transformationResult with the DataSet and Error objects
	 * @returns new DataSet
	 */
	readdTransformationErrors(transformationResult: TransformationResult): DataSet {
		var dataSet = transformationResult.dataSet; 

		var newDataSet = new DataSet(); 
		var dataArray = new Array<Array<any>>; 

		var readdedIndices = new List<number>(); 
		var rowCounter = 0;
		dataSet.data.forEach((dataRow) => {
			transformationResult.transformationErrors.forEach(error => {
				if (error.index == rowCounter && !readdedIndices.contains(rowCounter)) {
					readdedIndices.add(rowCounter);  
					rowCounter++;
					dataArray.push(error.rawValues); 
				}
			}); 
			dataArray.push(dataRow);
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
	getColumnNames(dataSet: DataSet): string[] {
		var result: string[] = [];

		dataSet.dataConfiguration.configurations.forEach((column) => {
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
