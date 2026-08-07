import {
  ConfigurationImportParameters,
  ConfigurationImportSummary,
  ConfigurationImportSummaryPart,
} from './import-pipe-data';

describe('import-pipe-data', () => {
  it('should create an instance', () => {
    expect(new ConfigurationImportParameters()).toBeTruthy();
    expect(new ConfigurationImportSummaryPart()).toBeTruthy();
    expect(new ConfigurationImportSummary()).toBeTruthy();
  });
});
