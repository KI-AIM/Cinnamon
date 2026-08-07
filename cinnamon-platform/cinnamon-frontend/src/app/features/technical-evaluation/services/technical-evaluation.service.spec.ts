import { HttpClient } from '@angular/common/http';
import { TechnicalEvaluationService } from './technical-evaluation.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('TechnicalEvaluationService', () => {
  let service: TechnicalEvaluationService;

  beforeEach(() => {
    service = new TechnicalEvaluationService(
      {} as HttpClient,
      new ConfigurationService({} as HttpClient),
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
