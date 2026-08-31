import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { StatisticsService } from './statistics.service';

describe('StatisticsService', () => {
    let service: StatisticsService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);
        service = new StatisticsService(httpClient, projectService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
