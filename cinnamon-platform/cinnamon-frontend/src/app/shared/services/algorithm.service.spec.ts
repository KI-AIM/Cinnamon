import { HttpClient } from '@angular/common/http';
import { Algorithm } from '../model/algorithm';
import { AlgorithmService, ReadConfigResult } from './algorithm.service';
import { ConfigurationService } from './configuration.service';

class TestAlgorithmService extends AlgorithmService {
  constructor(http: HttpClient, configurationService: ConfigurationService) {
    super(http, configurationService);
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
    service = new TestAlgorithmService(
      {} as HttpClient,
      new ConfigurationService({} as HttpClient),
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
