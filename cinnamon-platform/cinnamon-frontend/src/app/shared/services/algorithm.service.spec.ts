import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { Algorithm } from '../model/algorithm';
import { AlgorithmService, ReadConfigResult } from './algorithm.service';
import { ConfigurationService } from './configuration.service';

class TestAlgorithmService extends AlgorithmService {
  constructor(http: HttpClient, configurationService: ConfigurationService, projectService: ProjectService) {
    super(http, configurationService, projectService);
  }

  override getConfigurationName(): string {
    return 'test';
  }

  override createConfiguration(_: Object, __: Algorithm): Object {
    return {};
  }

  override readConfiguration(_: Object, __: string): ReadConfigResult {
    return { config: {}, selectedAlgorithm: new Algorithm() };
  }
}

describe('AlgorithmService', () => {
  let service: TestAlgorithmService;

  beforeEach(() => {
      const httpClient = {} as HttpClient; // Mock HttpClient
      const projectService = new ProjectService(httpClient);
      service = new TestAlgorithmService(
          httpClient,
          new ConfigurationService(httpClient, projectService),
          projectService,
      );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
