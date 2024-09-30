import { TestBed } from '@angular/core/testing';

import { TechnicalEvaluationService } from './technical-evaluation.service';

describe('TechnicalEvaluationService', () => {
  let service: TechnicalEvaluationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TechnicalEvaluationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
