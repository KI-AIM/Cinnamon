/* tslint:disable:no-unused-variable */

import { TestBed, async, inject } from '@angular/core/testing';
import { AnonymizationAttributeConfigurationService } from './anonymization-attribute-configuration.service';

describe('Service: AnonymizationAttributeConfiguration', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AnonymizationAttributeConfigurationService]
    });
  });

  it('should ...', inject([AnonymizationAttributeConfigurationService], (service: AnonymizationAttributeConfigurationService) => {
    expect(service).toBeTruthy();
  }));
});
