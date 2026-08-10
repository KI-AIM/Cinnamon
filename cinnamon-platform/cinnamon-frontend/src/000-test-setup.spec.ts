import { HttpClient } from '@angular/common/http';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProjectService } from "@shared/services/project.service";
import { Algorithm } from './app/shared/model/algorithm';
import { AlgorithmService, ReadConfigResult } from './app/shared/services/algorithm.service';
import { ConfigurationService } from './app/shared/services/configuration.service';

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

beforeEach(() => {
  TestBed.configureTestingModule({
    imports: [
      FormsModule,
      ReactiveFormsModule,
      RouterTestingModule,
      NoopAnimationsModule,
    ],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: AlgorithmService,
          useFactory: () => new TestAlgorithmService(
              {} as HttpClient,
              new ConfigurationService({} as HttpClient, new ProjectService({} as HttpClient)),
              new ProjectService({} as HttpClient)
        ),
      },
    ],
    schemas: [NO_ERRORS_SCHEMA],
  });
});

describe('test setup', () => {
  it('registers shared TestBed defaults', () => {
    expect(true).toBeTrue();
  });
});
