import { Component, ViewChild } from "@angular/core";
import { TransformationService } from "../../services/transformation.service";
import { DataSet } from "src/app/shared/model/data-set";
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";

@Component({
	selector: "app-data-table",
	templateUrl: "./data-table.component.html",
	styleUrls: ["./data-table.component.less"],
})
export class DataTableComponent {
	dataSource = new MatTableDataSource<TableElement>(
		this.transformDataSet(
			this.transformationService.getTransformationResult().dataSet
		)
	);
	@ViewChild(MatPaginator) paginator: MatPaginator;

	constructor(public transformationService: TransformationService) {}

	ngAfterViewInit() {
		this.dataSource.paginator = this.paginator;
	}

	transformDataSet(dataSet: DataSet): TableElement[] {
		const transformedData: TableElement[] = [];

		dataSet.data.forEach((dataRow) => {
			const transformedRow: TableElement = {};
			dataRow.forEach((dataItem, index) => {
				const columnName =
					dataSet.dataConfiguration.configurations[index].name;
				transformedRow[columnName as string] = dataItem.toString();
			});
			transformedData.push(transformedRow);
		});

		return transformedData;
	}

	getColumnNames(dataSet: DataSet): string[] {
		var result: string[] = [];

		dataSet.dataConfiguration.configurations.forEach((column) => {
			result.push(column.name as string);
		});

		return result;
	}
}

interface TableElement {
	[key: string]: any;
}
