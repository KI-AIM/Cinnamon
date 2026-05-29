import { ColumnConfiguration } from "./column-configuration";
import { DataConfiguration, hasTextColumns } from "./data-configuration";
import { DataType } from "./data-type";

describe("DataConfiguration", () => {
	it("should create an instance", () => {
		expect(new DataConfiguration()).toBeTruthy();
	});

	it("should detect text columns", () => {
		const dataConfiguration = new DataConfiguration();
		const textColumn = new ColumnConfiguration();
		textColumn.type = DataType.TEXT;
		dataConfiguration.configurations = [textColumn];

		expect(hasTextColumns(dataConfiguration)).toBeTrue();
	});
});
