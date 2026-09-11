import { HttpClient } from '@angular/common/http';
import { ProjectService } from "@shared/services/project.service";
import { SynthetizationService } from './synthetization.service';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

describe('SynthetizationService', () => {
  let service: SynthetizationService;

    beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const projectService = new ProjectService(httpClient);
        service = new SynthetizationService(
            httpClient,
            new ConfigurationService(httpClient, projectService),
            projectService
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
  for (const name of ['ctgan', 'tvae', 'arf', 'ddpm', 'rtvae', 'bayesian_network']) {
    it(`round-trips ${name} parameters, tuning and the text method`, () => {
      const algorithm = {name, type: 'cross-sectional', version: '0.1'} as any;
      (service as any)._algorithms = [algorithm];
      const tuning = {enabled: true, sampler: 'grid', pruner: 'none', n_trials: 2, timeout_minutes: 5};
      service.setHyperparameterConfig(tuning);
      const values = {
        model_parameter: {number_of_layers: 3},
        model_fitting: {epochs: 17, batch_size: 32},
        sampling: {num_samples: 123},
        text_synthesis_configuration: {synthetization_configuration: {algorithm: {
          synthesizer: 'llm_mixed_data_embedding_nearest_neighbor_synthesis',
          model_parameter: {few_shot_examples: 5, structured_similarity_weight: 1, text_similarity_weight: 0},
          sampling: {temperature: 0.4},
        }}},
      };
      const serialized = service.createConfiguration(values, algorithm);
      const restored = service.readConfiguration(serialized, 'synthetization_configuration');
      expect(restored.config).toEqual(values);
      expect(restored.selectedAlgorithm).toEqual(algorithm);
      expect(service.getHyperparameterConfig()).toEqual(tuning);
    });
  }

});
