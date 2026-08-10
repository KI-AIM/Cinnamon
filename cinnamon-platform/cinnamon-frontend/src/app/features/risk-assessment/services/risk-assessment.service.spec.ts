import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { RiskAssessmentService } from './risk-assessment.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('RiskAssessmentService', () => {
    let service: RiskAssessmentService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);

        service = new RiskAssessmentService(
            new ConfigurationService(httpClient, projectService),
            httpClient,
            projectService,
        );
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
