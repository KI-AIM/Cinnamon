import { HttpClient } from '@angular/common/http';
import { DataConfigurationService } from "./data-configuration.service";
import { ConfigurationService } from './configuration.service';

describe("DataConfigurationService", () => {
	let service: DataConfigurationService;

	beforeEach(() => {
		service = new DataConfigurationService(
			{} as HttpClient,
			new ConfigurationService({} as HttpClient),
			{ addError: jasmine.createSpy('addError') } as any,
		);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
