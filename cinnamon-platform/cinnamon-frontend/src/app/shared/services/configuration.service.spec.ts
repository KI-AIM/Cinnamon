import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { ConfigurationService } from './configuration.service';

describe('ConfigurationService', () => {
    let service: ConfigurationService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        service = new ConfigurationService(httpClient, new ProjectService(httpClient));
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
