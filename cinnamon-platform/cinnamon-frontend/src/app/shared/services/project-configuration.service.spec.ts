import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { ProjectConfigurationService } from './project-configuration.service';

describe('ProjectConfigurationService', () => {
    let service: ProjectConfigurationService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);
        service = new ProjectConfigurationService(
            httpClient,
            projectService,
            {} as any,
        );
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
