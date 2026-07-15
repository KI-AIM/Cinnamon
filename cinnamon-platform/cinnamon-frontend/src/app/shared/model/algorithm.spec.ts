import {
  Algorithm,
  isMixedTextSynthesizer,
  isStructuredOnlySynthesizer,
  isTextOnlySynthesizer,
  supportsFreeTextData,
  supportsStructuredData,
} from './algorithm';

describe('Algorithm', () => {
  it('should create an instance', () => {
    expect(new Algorithm()).toBeTruthy();
  });

  it('should resolve processing capabilities from YAML metadata', () => {
    const algorithm = new Algorithm();
    algorithm.name = 'ctgan';
    algorithm.processing_capabilities = {
      data_modality: 'structured_only',
      generation_scope: 'structured_only',
    };

    expect(supportsStructuredData(algorithm)).toBeTrue();
    expect(supportsFreeTextData(algorithm)).toBeFalse();
    expect(isTextOnlySynthesizer(algorithm)).toBeFalse();
    expect(isStructuredOnlySynthesizer(algorithm)).toBeTrue();
  });

  it('should fall back to legacy name-based detection if capabilities are missing', () => {
    const algorithm = new Algorithm();
    algorithm.name = 'llm_text_synthesis';

    expect(supportsStructuredData(algorithm)).toBeTrue();
    expect(supportsFreeTextData(algorithm)).toBeTrue();
    expect(isTextOnlySynthesizer(algorithm)).toBeFalse();
    expect(isMixedTextSynthesizer(algorithm)).toBeTrue();
  });

  it('should detect text-only synthesizers from metadata', () => {
    const algorithm = new Algorithm();
    algorithm.name = 'llm_text_only_paraphrase_synthesis';
    algorithm.processing_capabilities = {
      data_modality: 'text_only',
      generation_scope: 'text_only',
    };

    expect(supportsStructuredData(algorithm)).toBeFalse();
    expect(supportsFreeTextData(algorithm)).toBeTrue();
    expect(isTextOnlySynthesizer(algorithm)).toBeTrue();
  });

  it('should recognize a mixed-generation synthesizer as supporting both data kinds', () => {
    const algorithm = new Algorithm();
    algorithm.name = 'llm_mixed_data_paraphrase_synthesis';
    algorithm.processing_capabilities = {
      data_modality: 'mixed',
      generation_scope: 'mixed',
    };

    expect(supportsStructuredData(algorithm)).toBeTrue();
    expect(supportsFreeTextData(algorithm)).toBeTrue();
    expect(isMixedTextSynthesizer(algorithm)).toBeFalse();
  });
});
