import { TestBed } from '@angular/core/testing';

import { AlgorithmServiceService } from './algorithm-service.service';

describe('AlgorithmServiceService', () => {
  let service: AlgorithmServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AlgorithmServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
