import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { DataService } from "./data.service";

describe("DataService", () => {
	let service: DataService;

	beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);
		service = new DataService(httpClient, projectService);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
