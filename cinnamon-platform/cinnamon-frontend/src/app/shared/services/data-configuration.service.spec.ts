import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { DataConfigurationService } from "./data-configuration.service";
import { ConfigurationService } from './configuration.service';

describe("DataConfigurationService", () => {
	let service: DataConfigurationService;

	beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);
		service = new DataConfigurationService(
			httpClient,
			new ConfigurationService(httpClient, projectService),
			{ addError: jasmine.createSpy('addError') } as any,
            projectService,
		);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
