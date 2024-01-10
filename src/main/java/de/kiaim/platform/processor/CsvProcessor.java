package de.kiaim.platform.processor;

import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.file.CsvFileConfiguration;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Service
public class CsvProcessor extends CommonDataProcessor implements DataProcessor {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransformationResult read(InputStream data, FileConfiguration fileConfiguration,
	                                 DataConfiguration configuration) {
		final CsvFileConfiguration csvFileConfiguration = fileConfiguration.getCsvFileConfiguration();
		final CSVFormat csvFormat = buildCsvFormat(csvFileConfiguration);

		final Iterable<CSVRecord> records;
		try {
			records = csvFormat.parse(new InputStreamReader(data));
		} catch (IOException e) {
			// TODO catch Error
			throw new RuntimeException(e);
		}

		final Iterator<CSVRecord> recordIterator = records.iterator();
		if (recordIterator.hasNext() && csvFileConfiguration.isHasHeader()) {
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
	public DataConfiguration estimateDatatypes(InputStream data, FileConfiguration fileConfiguration) {
		final CsvFileConfiguration csvFileConfiguration = fileConfiguration.getCsvFileConfiguration();
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

		String[] columnNames =  null;
		if (csvFileConfiguration.isHasHeader()) {
			columnNames = records.iterator().next().values();
		}

		List<String[]> validRows = getSubsetOfCompleteRows(recordIterator, 10);

		if (!csvFileConfiguration.isHasHeader()) {
			columnNames = new String[validRows.get(0).length];
			Arrays.fill(columnNames, "");
		}

		final List<DataType> estimatedDataTypes;
		if (validRows.isEmpty()) {
			estimatedDataTypes = getUndefinedDatatypesList(columnNames.length);
		} else {
			estimatedDataTypes = estimateDatatypesForMultipleRows(validRows);
		}

		return buildConfigurationForDataTypes(estimatedDataTypes, Arrays.asList(columnNames));
	}

	/**
	 * Function that returns a subset of complete rows for csv records.
	 * Complete means that no missing value should be present in a row.
	 * The amount of rows is limited by the parameter maxNumberOfRows.
	 *
	 * @param recordIterator Iterator of csv records
	 * @param maxNumberOfRows the maximum number of rows
	 * @return A List<String[]> of split rows
	 */
	private List<String[]> getSubsetOfCompleteRows(Iterator<CSVRecord> recordIterator, int maxNumberOfRows) {
		List<String[]> validRows = new ArrayList<>();

		while (recordIterator.hasNext() && validRows.size() < maxNumberOfRows) {
			CSVRecord record = recordIterator.next();
			String[] row = record.values();

			if (isColumnListComplete(row)) {
				validRows.add(row);
			}
		}

		return validRows;
	}

	private CSVFormat buildCsvFormat(final CsvFileConfiguration csvFileConfiguration) {
		return CSVFormat.DEFAULT.builder()
		                        .setDelimiter(csvFileConfiguration.getColumnSeparator())
		                        .setRecordSeparator(csvFileConfiguration.getLineSeparator())
		                        .setQuote(csvFileConfiguration.getQuoteChar())
		                        .build();
	}
}
