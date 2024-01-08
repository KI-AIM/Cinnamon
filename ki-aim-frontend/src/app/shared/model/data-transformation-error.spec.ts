import { DataTransformationError } from "./data-transformation-error";

describe("DataTransformationError", () => {
	it("should create an instance", () => {
		expect(new DataTransformationError()).toBeTruthy();
	});
});
