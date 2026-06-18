import { HttpClient } from '@angular/common/http';
import { SynthetizationService } from './synthetization.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('SynthetizationService', () => {
  let service: SynthetizationService;

  beforeEach(() => {
    service = new SynthetizationService(
      {} as HttpClient,
      new ConfigurationService({} as HttpClient),
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
