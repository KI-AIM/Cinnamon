import { HttpClient } from '@angular/common/http';
import { DataSetInfoService } from './data-set-info.service';

describe('DataSetInfoService', () => {
  let service: DataSetInfoService;

  beforeEach(() => {
    service = new DataSetInfoService({} as HttpClient);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
