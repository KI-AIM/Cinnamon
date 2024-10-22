import { TestBed } from '@angular/core/testing';

import { SynthetizationServiceService } from './synthetization-service.service';

describe('SynthetizationServiceService', () => {
  let service: SynthetizationServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SynthetizationServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
