import { HttpClient } from '@angular/common/http';
import { RiskAssessmentService } from './risk-assessment.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('RiskAssessmentService', () => {
  let service: RiskAssessmentService;

  beforeEach(() => {
    service = new RiskAssessmentService(
      new ConfigurationService({} as HttpClient),
      {} as HttpClient,
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
