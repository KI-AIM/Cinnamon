import { TestBed } from "@angular/core/testing";

import { DataConfigurationService } from "./data-configuration.service";

describe("DataConfigurationService", () => {
	let service: DataConfigurationService;

	beforeEach(() => {
		TestBed.configureTestingModule({});
		service = TestBed.inject(DataConfigurationService);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
