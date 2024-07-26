import { TestBed } from '@angular/core/testing';

import { StateGuard } from './state.guard';

describe('StateGuard', () => {
  let guard: StateGuard;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    guard = TestBed.inject(StateGuard);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });
});
