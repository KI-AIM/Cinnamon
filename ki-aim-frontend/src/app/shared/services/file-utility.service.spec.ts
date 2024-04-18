import { TestBed } from '@angular/core/testing';

import { FileUtilityService } from './file-utility.service';

describe('FileUtilityService', () => {
  let service: FileUtilityService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FileUtilityService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
