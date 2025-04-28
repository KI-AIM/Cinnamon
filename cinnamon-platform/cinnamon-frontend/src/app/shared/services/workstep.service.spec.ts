import { TestBed } from '@angular/core/testing';

import { WorkstepService } from './workstep.service';

describe('WorkstepService', () => {
  let service: WorkstepService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WorkstepService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
