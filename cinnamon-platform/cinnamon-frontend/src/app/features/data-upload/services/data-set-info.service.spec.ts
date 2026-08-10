import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { DataSetInfoService } from './data-set-info.service';

describe('DataSetInfoService', () => {
    let service: DataSetInfoService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        service = new DataSetInfoService(httpClient, new ProjectService(httpClient));
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
