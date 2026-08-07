import { HttpClient } from '@angular/common/http';
import { AppConfigService } from './app-config.service';

describe('AppConfigService', () => {
  let service: AppConfigService;

  beforeEach(() => {
    service = new AppConfigService(
      {} as HttpClient,
      { addError: jasmine.createSpy('addError') } as any,
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
