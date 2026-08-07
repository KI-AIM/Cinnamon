import { CsvFileConfiguration, Delimiter, LineEnding, QuoteChar } from './csv-file-configuration';

describe('CsvFileConfiguration', () => {
  it('should create an instance', () => {
    expect(new CsvFileConfiguration(
      Delimiter.COMMA,
      LineEnding.LF,
      QuoteChar.DOUBLE_QUOTE,
      true,
    )).toBeTruthy();
  });
});
