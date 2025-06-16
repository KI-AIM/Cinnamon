package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.FileType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class CsvProcessor extends CommonDataProcessor implements DataProcessor {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FileType getSupportedDataType() {
		return FileType.CSV;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberColumns(final InputStream data, final FileConfigurationEntity fileConfiguration) {
		final CsvFileConfigurationEntity csvFileConfiguration = (CsvFileConfigurationEntity) fileConfiguration;
		final CSVFormat csvFormat = buildCsvFormat(csvFileConfiguration);

		final Iterable<CSVRecord> records;
		try {
			records = csvFormat.parse(new InputStreamReader(data));
		} catch (IOException e) {
			// TODO catch Error
			throw new RuntimeException(e);
		}

		return records.iterator().next().size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransformationResult read(InputStream data, FileConfigurationEntity fileConfiguration,
	                                 DataConfiguration configuration) {
		final CsvFileConfigurationEntity csvFileConfiguration = (CsvFileConfigurationEntity) fileConfiguration;
		final CSVFormat csvFormat = buildCsvFormat(csvFileConfiguration);

		final Iterable<CSVRecord> records;
		try {
			records = csvFormat.parse(new InputStreamReader(data));
		} catch (IOException e) {
			// TODO catch Error
			throw new RuntimeException(e);
		}

		final Iterator<CSVRecord> recordIterator = records.iterator();
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
	public DataConfiguration estimateDataConfiguration(InputStream data, FileConfigurationEntity fileConfiguration,
	                                                   final DatatypeEstimationAlgorithm algorithm) {
		final CsvFileConfigurationEntity csvFileConfiguration = (CsvFileConfigurationEntity) fileConfiguration;
		final CSVFormat csvFormat = buildCsvFormat(csvFileConfiguration);

		final Iterable<CSVRecord> records;
		try {
			records = csvFormat.parse(new InputStreamReader(data));
		} catch (IOException e) {
			// TODO catch Error
			throw new RuntimeException(e);
		}

		final Iterator<CSVRecord> recordIterator = records.iterator();
		if (!recordIterator.hasNext()) {
			return new DataConfiguration();
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
				if (attributeSamples.size() < NUMBER_OF_SAMPLES) {
					attributeSamples.add(firstRow.get(i));
				} else {
					attributeSamples.set(NUMBER_OF_SAMPLES - 1, firstRow.get(i));
				}
			}
		}

		return estimateDataConfiguration(samples, algorithm, numberColumns, columnNames);
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
}
