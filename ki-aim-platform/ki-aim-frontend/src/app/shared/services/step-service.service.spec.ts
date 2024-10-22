import { TestBed } from '@angular/core/testing';

import { StepServiceService } from './step-service.service';

describe('StepServiceService', () => {
  let service: StepServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StepServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
