import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { TechnicalEvaluationService } from './technical-evaluation.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('TechnicalEvaluationService', () => {
    let service: TechnicalEvaluationService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);
        service = new TechnicalEvaluationService(
            httpClient,
            new ConfigurationService(httpClient, projectService),
            projectService,
        );
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
