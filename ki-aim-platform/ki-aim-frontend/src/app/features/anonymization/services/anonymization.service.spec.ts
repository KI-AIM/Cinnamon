import { TestBed } from '@angular/core/testing';

import { AnonymizationServiceService } from './anonymization-service.service';

describe('AnonymizationServiceService', () => {
  let service: AnonymizationServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AnonymizationServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
