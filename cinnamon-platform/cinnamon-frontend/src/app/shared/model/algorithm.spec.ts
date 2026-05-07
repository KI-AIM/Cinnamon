import {
  Algorithm,
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
      supports_structured_data: true,
      supports_free_text_data: false,
    };

    expect(supportsStructuredData(algorithm)).toBeTrue();
    expect(supportsFreeTextData(algorithm)).toBeFalse();
    expect(isTextOnlySynthesizer(algorithm)).toBeFalse();
  });

  it('should fall back to legacy name-based detection if capabilities are missing', () => {
    const algorithm = new Algorithm();
    algorithm.name = 'llm_text_synthesis';

    expect(supportsStructuredData(algorithm)).toBeFalse();
    expect(supportsFreeTextData(algorithm)).toBeTrue();
    expect(isTextOnlySynthesizer(algorithm)).toBeTrue();
  });
});
