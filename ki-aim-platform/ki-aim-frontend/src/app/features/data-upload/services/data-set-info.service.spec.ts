import { TestBed } from '@angular/core/testing';

import { DataSetInfoService } from './data-set-info.service';

describe('DataSetInfoService', () => {
  let service: DataSetInfoService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DataSetInfoService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
