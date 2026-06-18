import { HttpClient } from '@angular/common/http';
import { StatisticsService } from './statistics.service';

describe('StatisticsService', () => {
  let service: StatisticsService;

  beforeEach(() => {
    service = new StatisticsService({} as HttpClient);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
