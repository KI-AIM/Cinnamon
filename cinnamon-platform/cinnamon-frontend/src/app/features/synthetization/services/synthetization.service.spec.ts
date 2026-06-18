import { HttpClient } from '@angular/common/http';
import { SynthetizationService } from './synthetization.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('SynthetizationService', () => {
  let service: SynthetizationService;

  beforeEach(() => {
    service = new SynthetizationService(
      {} as HttpClient,
      new ConfigurationService({} as HttpClient),
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('stores text synthesis configuration inside synthetization_configuration', () => {
    const config = service.createConfiguration({
      model_parameter: { epochs: 10 },
      text_synthesis_configuration: {
        synthetization_configuration: {
          algorithm: {
            llm_profile: {
              llm_profile: 'Ollama Qwen3 8B (lokal)',
            },
          },
        },
      },
    }, {
      name: 'ctgan',
      version: '1.0',
      type: 'cross-sectional',
    } as any) as any;

    expect(config.text_synthesis_configuration).toBeUndefined();
    expect(config.synthetization_configuration.text_synthesis_configuration).toEqual({
      synthetization_configuration: {
        algorithm: {
          llm_profile: {
            llm_profile: 'Ollama Qwen3 8B (lokal)',
          },
        },
      },
    });
  });

  it('reads nested text synthesis configuration from stored config', () => {
    (service as any)._algorithms = [{
      name: 'ctgan',
      version: '1.0',
      type: 'cross-sectional',
    }];

    const result = service.readConfiguration({
      synthetization_configuration: {
        algorithm: {
          id: 'ctgan',
          synthesizer: 'ctgan',
          type: 'cross-sectional',
          version: '1.0',
          hyperparameter_tuning: { enabled: false },
        },
        text_synthesis_configuration: {
          synthetization_configuration: {
            algorithm: {
              llm_profile: {
                llm_profile: 'Ollama Qwen3 8B (lokal)',
              },
            },
          },
        },
      },
    }, 'synthetization_configuration');

    expect((result.config as any).text_synthesis_configuration).toEqual({
      synthetization_configuration: {
        algorithm: {
          llm_profile: {
            llm_profile: 'Ollama Qwen3 8B (lokal)',
          },
        },
      },
    });
  });
});
