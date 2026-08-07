import { FormBuilder } from '@angular/forms';
import { AnonymizationAttributeConfigurationService } from './anonymization-attribute-configuration.service';

describe('Service: AnonymizationAttributeConfiguration', () => {
  let service: AnonymizationAttributeConfigurationService;

  beforeEach(() => {
    service = new AnonymizationAttributeConfigurationService(new FormBuilder());
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });
});
