package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.dto.FileConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.CsvDelimiter;
import de.kiaim.cinnamon.platform.model.enumeration.CsvQuote;
import de.kiaim.cinnamon.platform.model.enumeration.CsvRecordSeparator;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.CsvFileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileType;
import de.kiaim.cinnamon.platform.service.DataSetService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CsvProcessor extends CommonDataProcessor implements DataProcessor {

	private final DataSetService dataSetService;

	public CsvProcessor(final DataSetService dataSetService) {
		this.dataSetService = dataSetService;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FileType getSupportedDataType() {
		return FileType.CSV;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Estimates the record separator, delimiter, quote char, and if the file has a header.
	 * If the data does not contain a quote char, a double quote is used as default.
	 */
	@Override
	public FileConfigurationEstimation estimateFileConfiguration(final InputStream data) throws InternalIOException {
		final byte[] bytes;
		try {
			bytes = data.readAllBytes();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.FILE_READING,
			                              "Failed to read the file while estimating the CSV file configuration", e);
		}

		final var csvFileConfiguration = estimateCsvFileConfiguration(new String(bytes));
		final var fileConfiguration =  new FileConfiguration();

		fileConfiguration.setFileType(FileType.CSV);
		fileConfiguration.setCsvFileConfiguration(csvFileConfiguration);

		return new FileConfigurationEstimation(fileConfiguration);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberColumns(final InputStream data,
	                            final FileConfigurationEntity fileConfiguration) throws InternalIOException {
		return getFirstRow(data, fileConfiguration).size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransformationResult read(InputStream data, FileConfigurationEntity fileConfiguration,
	                                 DataConfiguration configuration) throws InternalIOException {
		final CsvFileConfigurationEntity csvFileConfiguration = (CsvFileConfigurationEntity) fileConfiguration;

		final Iterator<CSVRecord> recordIterator = getRecords(data, fileConfiguration);
		if (recordIterator.hasNext() && csvFileConfiguration.getHasHeader()) {
			recordIterator.next();
		}

		final List<DataRow> dataRows = new ArrayList<>();
		final List<DataRowTransformationError> errors = new ArrayList<>();
		int rowIndex = 0;
		while (recordIterator.hasNext()) {
			transformRow(Arrays.asList(recordIterator.next().values()), rowIndex, configuration, dataRows, errors);
			rowIndex += 1;
		}

		return new TransformationResult(new DataSet(dataRows, configuration), errors);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DataConfigurationEstimation estimateDataConfiguration(InputStream data,
	                                                             FileConfigurationEntity fileConfiguration,
	                                                             final DatatypeEstimationAlgorithm algorithm) throws InternalIOException {
		final CsvFileConfigurationEntity csvFileConfiguration = (CsvFileConfigurationEntity) fileConfiguration;

		final Iterator<CSVRecord> recordIterator = getRecords(data, fileConfiguration);
		if (!recordIterator.hasNext()) {
			return new DataConfigurationEstimation(new DataConfiguration(), new float[0]);
		}

		final int numberColumns;
		final List<String> columnNames;
		final List<List<String>> samples;

		if (csvFileConfiguration.getHasHeader()) {
			columnNames = normalizeColumnNames(recordIterator.next().values());
			numberColumns = columnNames.size();

			samples = getAttributeSamples(recordIterator, numberColumns);
		} else {
			CSVRecord firstRecord = recordIterator.next();
			List<String> firstRow = Arrays.asList(firstRecord.values());
			numberColumns = firstRow.size();
			columnNames = Collections.nCopies(numberColumns, "");

			samples = getAttributeSamples(recordIterator, numberColumns);
			for (int i = 0; i < numberColumns; i++) {
				List<String> attributeSamples = samples.get(i);
				if (attributeSamples.size() < maxSampleSize) {
					attributeSamples.add(firstRow.get(i));
				} else {
					attributeSamples.set(maxSampleSize - 1, firstRow.get(i));
				}
			}
		}

		return estimateDataConfiguration(samples, algorithm, numberColumns, columnNames);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(final OutputStream outputStream, final DataSet dataset) throws InternalIOException {
		final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
		final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(
				dataset.getDataConfiguration().getColumnNames().toArray(new String[0])).build();

		try {
			final CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, csvFormat);
			for (final List<Object> dataRow : dataSetService.encodeDataRowsSimple(dataset)) {
				csvPrinter.printRecord(dataRow);
			}
			csvPrinter.flush();
		} catch (IOException e) {
			throw new InternalIOException(InternalIOException.CSV_CREATION, "Failed to create the CVS file!", e);
		}
	}

	/**
	 * Returns the first row of the CSV file.
	 *
	 * @param data The CSV file.
	 * @param fileConfiguration The file configuration describing the format of the CSV file.
	 * @return The first row of the file.
	 * @throws InternalIOException If reading the CSV file failed.
	 */
	public List<String> getFirstRow(final InputStream data,
	                                final FileConfigurationEntity fileConfiguration) throws InternalIOException {
		final Iterator<CSVRecord> recordIterator = getRecords(data, fileConfiguration);
		return recordIterator.next().toList();
	}

	/**
	 * Builds a CSVFormat form Apache Commons CSV based on the passed configurations of the CsvFileConfiguration.
	 *
	 * @param csvFileConfiguration Configuration to configure the CSVFormt
	 * @return The configured CSVFormat
	 */
	private CSVFormat buildCsvFormat(final CsvFileConfigurationEntity csvFileConfiguration) {
		return CSVFormat.DEFAULT.builder()
		                        .setDelimiter(csvFileConfiguration.getColumnSeparator())
		                        .setRecordSeparator(csvFileConfiguration.getLineSeparator())
		                        .setQuote(csvFileConfiguration.getQuoteChar())
		                        .build();
	}

	/**
	 * Creates an CSVRecord iterator for the given CSV file.
	 *
	 * @param data The CSV file.
	 * @param fileConfiguration The file configuration describing the format of the CSV file.
	 * @return Record iterator.
	 * @throws InternalIOException If reading the CSV file failed.
	 */
	private Iterator<CSVRecord> getRecords(final InputStream data,
	                                       final FileConfigurationEntity fileConfiguration) throws InternalIOException {
		final CsvFileConfigurationEntity csvFileConfiguration = (CsvFileConfigurationEntity) fileConfiguration;
		final CSVFormat csvFormat = buildCsvFormat(csvFileConfiguration);

		final Iterable<CSVRecord> records;
		try {
			records = csvFormat.parse(new InputStreamReader(data));
		} catch (IOException e) {
			throw new InternalIOException(InternalIOException.CSV_READING, "Failed to parse CSV file", e);
		}

		return records.iterator();
	}

	/**
	 * Estimates the CSV-specific configuration.
	 * If the data does not contain a quote char, a double quote is used as default.
	 *
	 * @param csv The CSV content.
	 * @return The estimated CSV configuration.
	 */
	private CsvFileConfiguration estimateCsvFileConfiguration(final String csv) {
		final var config = new CsvFileConfiguration();

		estimateRecordSeparator(csv, config);
		String[] records = csv.split(config.getLineSeparator());
		estimateDelimiter(records, config);
		estimateQuoteChar(records, config);
		estimateHasHeader(records, config);

		return config;
	}

	/**
	 * Estimates the record separator by checking which of the line endings is contained in the file.
	 * Writes the result in the given configuration.
	 *
	 * @param csv The CSV content.
	 * @param config The configuration to estimate.
	 */
	private void estimateRecordSeparator(final String csv, final CsvFileConfiguration config) {
		final var recordSeparators = Arrays.stream(CsvRecordSeparator.values())
		                                   .sorted(Comparator.comparingInt(CsvRecordSeparator::getDetectionOrder))
		                                   .toList();
		for (final var separator : recordSeparators) {
			final var separatorString = separator.getSeparator();
			if (csv.contains(separatorString)) {
				config.setLineSeparator(separatorString);
				break;
			}
		}
	}

	/**
	 * Estimates the delimiter by counting the occurrences for each delimiter in each row and selecting the most used one.
	 * Writes the result in the given configuration.
	 *
	 * @param records Records in the CSV file.
	 * @param config The configuration to estimate.
	 */
	private void estimateDelimiter(final String[] records, final CsvFileConfiguration config) {
		final Map<CsvDelimiter, Long> delimiterFrequencies = new HashMap<>();
		for (final var delimiter : CsvDelimiter.values()) {
			List<Integer> occurrencesForDelimiter = new ArrayList<>();
			for (int i = 0; i < Math.min(maxSampleSize, records.length); i++) {
				final String row = records[i];
				final int count = row.length() - row.replace(delimiter.getDelimiterAsString(), "").length();
				occurrencesForDelimiter.add(count);
			}

			Map<Integer, Long> frequencyMap = occurrencesForDelimiter.stream()
			                                                         .collect(Collectors.groupingBy(i -> i,
			                                                                                        Collectors.counting()));
			long maxFrequency = frequencyMap.entrySet()
			                                .stream()
			                                .max(Comparator.comparingLong(Map.Entry::getValue))
			                                .map(Map.Entry::getKey)
			                                .orElse(0);
			delimiterFrequencies.put(delimiter, maxFrequency);
		}

		delimiterFrequencies.entrySet()
		                    .stream()
		                    .reduce((a, b) -> a.getValue() > b.getValue() ? a : b)
		                    .ifPresent(entry -> config.setColumnSeparator(entry.getKey().getDelimiterAsString()));
	}

	/**
	 * Estimates the quote char by counting the occurrences for each quote char at the start and end of each value the most used one.
	 * Writes the result in the given configuration.
	 *
	 * @param records Records in the CSV file.
	 * @param config The configuration to estimate.
	 */
	private void estimateQuoteChar(final String[] records, final CsvFileConfiguration config) {
		final Map<CsvQuote, Long> quoteFrequencies = new HashMap<>();
		for (final var quote : CsvQuote.values()) {
			long quoteCount = 0L;

			for (int i = 0; i < Math.min(maxSampleSize, records.length); i++) {
				final String row = records[i];
				final String[] values = row.split(config.getColumnSeparator());
				for (String value : values) {
					if (value.length() >= 2) {
						if (value.charAt(0) == quote.getQuote() &&
						    value.charAt(value.length() - 1) == quote.getQuote()) {
							quoteCount++;
						}
					}
				}
			}

			if (quoteCount > 0) {
				quoteFrequencies.put(quote, quoteCount);
			}
		}

		quoteFrequencies.entrySet()
		                .stream()
		                .reduce((a, b) -> a.getValue() > b.getValue() ? a : b)
		                .ifPresent(entry -> config.setQuoteChar(entry.getKey().getQuote()));
	}

	/**
	 * Estimates, if the records contain a header row, by checking for common attributes names and comparing datatypes.
	 * Writes the result in the given configuration.
	 *
	 * @param records The CSV records.
	 * @param config  The configuration to estimate.
	 */
	private void estimateHasHeader(final String[] records, final CsvFileConfiguration config) {
		String[] header = records[0].split(config.getColumnSeparator());
		String[] second = records[1].split(config.getColumnSeparator());

		final boolean hasHeader = estimateHasHeader(List.of(header), List.of(second));
		config.setHasHeader(hasHeader);
	}

}
