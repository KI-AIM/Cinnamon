import { HttpClient } from '@angular/common/http';
import { ConfigurationService } from './configuration.service';

describe('ConfigurationService', () => {
  let service: ConfigurationService;

  beforeEach(() => {
    service = new ConfigurationService({} as HttpClient);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
