import { CsvFileConfiguration, Delimiter, LineEnding, QuoteChar } from './csv-file-configuration';
import { FileConfiguration, FhirFileConfiguration, FileType } from './file-configuration';
import { XlsxFileConfiguration } from './xlsx-file-configuration';

describe('FileConfiguration', () => {
  it('should create an instance', () => {
    expect(new FileConfiguration(
      FileType.CSV,
      new CsvFileConfiguration(Delimiter.COMMA, LineEnding.LF, QuoteChar.DOUBLE_QUOTE, true),
      new XlsxFileConfiguration(true),
      new FhirFileConfiguration('Patient'),
    )).toBeTruthy();
  });
});
