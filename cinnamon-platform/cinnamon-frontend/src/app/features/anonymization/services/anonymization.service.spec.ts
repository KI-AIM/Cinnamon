import { HttpClient } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { AnonymizationAttributeConfigurationService } from './anonymization-attribute-configuration.service';
import { AnonymizationService } from './anonymization.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('AnonymizationService', () => {
  let service: AnonymizationService;

  beforeEach(() => {
    service = new AnonymizationService(
      new AnonymizationAttributeConfigurationService(new FormBuilder()),
      {} as HttpClient,
      new ConfigurationService({} as HttpClient),
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
