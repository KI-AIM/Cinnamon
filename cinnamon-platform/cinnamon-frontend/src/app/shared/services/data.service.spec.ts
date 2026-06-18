import { HttpClient } from '@angular/common/http';
import { DataService } from "./data.service";

describe("DataService", () => {
	let service: DataService;

	beforeEach(() => {
		service = new DataService({} as HttpClient);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
