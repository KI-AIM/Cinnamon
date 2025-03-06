import { TestBed } from '@angular/core/testing';

import { RiskAssessmentService } from './risk-assessment.service';

describe('RiskAssessmentService', () => {
  let service: RiskAssessmentService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(RiskAssessmentService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
