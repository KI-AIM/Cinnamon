import { Component } from "@angular/core";
import { TransformationService } from "../../services/transformation.service";
import { DataSet } from "src/app/shared/model/data-set";

@Component({
	selector: "app-data-table",
	templateUrl: "./data-table.component.html",
	styleUrls: ["./data-table.component.less"],
})
export class DataTableComponent {
	constructor(
        public transformationService: TransformationService,
    ){
    }
	
	transformDataSet(dataSet: DataSet): TableElement[] {
		const transformedData: TableElement[] = [];
	
		dataSet.data.forEach((dataRow) => {
			const transformedRow: TableElement = {};
			dataRow.forEach((dataItem, index) => {
				const columnName = dataSet.dataConfiguration.configurations[index].name;
				transformedRow[columnName as string] = dataItem.toString();
			});
			transformedData.push(transformedRow);
		});
	
		return transformedData;
	}

	getColumnNames(dataSet: DataSet): string[] {
		var result: string[] = []; 
	
		dataSet.dataConfiguration.configurations.forEach(column => {
			result.push(column.name as string); 
		}); 
	
		return result; 
	}
}

interface TableElement {
	[key: string]: any;
}
