import { HttpClient } from '@angular/common/http';
import { ProjectConfigurationService } from './project-configuration.service';

describe('ProjectConfigurationService', () => {
  let service: ProjectConfigurationService;

  beforeEach(() => {
    service = new ProjectConfigurationService(
      {} as HttpClient,
      {} as any,
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
