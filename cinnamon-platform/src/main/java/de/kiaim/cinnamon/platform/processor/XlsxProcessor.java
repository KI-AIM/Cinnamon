package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.dto.FileConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileCompatibilityEntity;
import de.kiaim.cinnamon.platform.model.entity.LobWrapperEntity;
import de.kiaim.cinnamon.platform.model.entity.XlsxFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import de.kiaim.cinnamon.model.configuration.data.file.XlsxFileConfiguration;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Stream;

@Service
public class XlsxProcessor extends CommonDataProcessor implements DataProcessor {

    private final String cinnamonVersion;

    public XlsxProcessor(@Value("${cinnamon.version}") final String cinnamonVersion) {
        this.cinnamonVersion = cinnamonVersion;
    }

	@Override
    public FileType getSupportedDataType() {
        return FileType.XLSX;
    }

	/**
	 * {@inheritDoc}
	 */
    @Override
    public void checkFileCompatibility(final LobWrapperEntity data, final FileCompatibilityEntity fileCompatibility) {
        try(final ReadableWorkbook ignoredWb = new ReadableWorkbook(data.getLobStream())) {
            fileCompatibility.getCompatibleFileTypes().add(FileType.XLSX);
        } catch (final IOException ignored) {
        }
    }

	/**
	 * {@inheritDoc}
	 * Only reads the first two rows to estimate if the file contains a header.
	 */
	@Override
	public FileConfigurationEstimation estimateFileConfiguration(final LobWrapperEntity data,
	                                                             final FileCompatibilityEntity fileCompatibility)
			throws InternalIOException {
		final List<List<@Nullable String>> firstRows = withRows(
				data.getLobStream(), rows -> rows.limit(2).map(row -> getRawValues(row, getWidth(row))).toList());

		final var xlsxFileConfiguration = new XlsxFileConfiguration();
		// Keep the default if there are less than two rows to compare
		if (firstRows.size() >= 2) {
			final int width = Math.max(firstRows.get(0).size(), firstRows.get(1).size());
			xlsxFileConfiguration.setHasHeader(estimateHasHeader(toHeaderValues(firstRows.get(0), width),
			                                                     toHeaderValues(firstRows.get(1), width)));
		}

		final var fileConfiguration = new FileConfiguration();
		fileConfiguration.setFileType(FileType.XLSX);
		fileConfiguration.setXlsxFileConfiguration(xlsxFileConfiguration);

		return new FileConfigurationEstimation(fileConfiguration);
	}

	/**
	 * {@inheritDoc}
	 * The number of columns is the width of the widest row, as rows end at their last non-empty cell.
	 */
	@Override
	public int getNumberColumns(final InputStream data, final FileConfigurationEntity fileConfiguration)
			throws InternalIOException {
		return withRows(data, XlsxProcessor::getNumberColumns);
	}

	/**
	 * {@inheritDoc}
	 * Rows with fewer cells than configured columns are padded with missing values.
	 */
	@Override
	public TransformationResult read(final InputStream data, final FileConfigurationEntity fileConfiguration,
	                                 final DataConfiguration configuration)
			throws BadDatasetException, InternalIOException {
		final XlsxFileConfigurationEntity xlsxFileConfiguration = (XlsxFileConfigurationEntity) fileConfiguration;

		return withRows(data, rows -> {
			final Iterator<Row> rowIterator = rows.iterator();
			if (rowIterator.hasNext() && xlsxFileConfiguration.getHasHeader()) {
				rowIterator.next();
			}

			final List<DataRow> dataRows = new ArrayList<>();
			final List<DataRowTransformationError> errors = new ArrayList<>();
			int rowIndex = 0;
			while (rowIterator.hasNext()) {
				transformRow(getConvertedValues(rowIterator.next(), configuration), rowIndex, configuration,
				             dataRows, errors);
				rowIndex++;
			}

			return new TransformationResult(new DataSet(dataRows, configuration), errors);
		});
	}

	/**
	 * Converts the cells of the row based on the configuration.
	 * The row is padded with null values to the number of configured columns.
	 * Non-empty cells beyond the configured columns are kept as raw values,
	 * so the row is reported as containing too many values.
	 *
	 * @param row           The row.
	 * @param configuration The data configuration.
	 * @return The converted values.
	 */
	private List<@Nullable String> getConvertedValues(final Row row, final DataConfiguration configuration) {
		final List<ColumnConfiguration> columnConfigurations = configuration.getConfigurations();
		final int width = Math.max(columnConfigurations.size(), getWidth(row));

		final List<@Nullable String> values = new ArrayList<>(width);
		for (int i = 0; i < width; i++) {
			if (i >= row.getCellCount()) {
				values.add(null);
			} else if (i < columnConfigurations.size()) {
				values.add(getConvertedCellValue(row, i, columnConfigurations.get(i)));
			} else {
				values.add(row.getCellRawValue(i).orElse(null));
			}
		}
		return values;
	}

    private @Nullable String getConvertedCellValue(Row r, int index, ColumnConfiguration columnConfiguration) {

        if (index < r.getCellCount()) {
            switch (columnConfiguration.getType()) {
                case DATE -> {
                    try {
                        LocalDateTime date = r.getCellAsDate(index).orElse(null);
                        if (date != null) {
                            DateFormatConfiguration dateFormatConfiguration = columnConfiguration.getConfiguration(DateFormatConfiguration.class);

							final DateTimeFormatter formatter;
                            if (dateFormatConfiguration != null) {
	                            formatter = new DateData.DateDataBuilder().buildFormatter(
			                            dateFormatConfiguration.getDateFormatter());
                            } else {
								formatter = DateTimeFormatter.ISO_DATE;
                            }
							return date.format(formatter);
                        }
                    } catch (Exception ignored) {}
                }
                case DATE_TIME -> {
                    try {
                        LocalDateTime date = r.getCellAsDate(index).orElse(null);
                        if (date != null) {
	                        final var dateTimeFormatConfiguration = columnConfiguration.getConfiguration(
			                        DateTimeFormatConfiguration.class);

	                        final DateTimeFormatter formatter;
	                        if (dateTimeFormatConfiguration != null) {
		                        formatter = new DateTimeData.DateTimeDataBuilder().buildFormatter(
				                        dateTimeFormatConfiguration.getDateTimeFormatter());
	                        } else {
		                        formatter = DateTimeFormatter.ISO_DATE;
	                        }
	                        return date.format(formatter);
                        }
                    } catch (Exception ignored) {}
                }
                case INTEGER -> {
                    try {
                        BigDecimal number = r.getCellAsNumber(index).orElse(null);
                        if (number != null) {
                            return String.valueOf(number.intValue());
                        }
                    } catch (Exception ignored) {}
                }
                case DECIMAL -> {
                    try {
                        BigDecimal number = r.getCellAsNumber(index).orElse(null);
                        if (number != null) {
                            return String.valueOf(number.floatValue());
                        }
                    } catch (Exception ignored) {}
                }
                case STRING, TEXT, BOOLEAN -> {
                    return r.getCellRawValue(index).orElse(null);
                }
            }
        }
        return r.getCellRawValue(index).orElse(null);
    }


	/**
	 * {@inheritDoc}
	 * Reads the file twice: once for determining the number of columns and once for collecting the samples,
	 * which stops as soon as enough samples are available.
	 */
	@Override
	public DataConfigurationEstimation estimateDataConfiguration(final InputStream data,
	                                                             final FileConfigurationEntity fileConfiguration,
	                                                             final DatatypeEstimationAlgorithm algorithm)
			throws InternalIOException {
		final XlsxFileConfigurationEntity xlsxFileConfiguration = (XlsxFileConfigurationEntity) fileConfiguration;
		final byte[] content = readAllBytes(data);

		final int numberColumns = withRows(new ByteArrayInputStream(content), XlsxProcessor::getNumberColumns);
		if (numberColumns == 0) {
			return new DataConfigurationEstimation(new DataConfiguration(), new float[0]);
		}

		return withRows(new ByteArrayInputStream(content), rows -> {
			final Iterator<List<@Nullable String>> rowIterator =
					rows.map(row -> getRawValues(row, numberColumns)).iterator();

			final List<String> columnNames;
			if (xlsxFileConfiguration.getHasHeader() && rowIterator.hasNext()) {
				columnNames = normalizeColumnNames(
						toHeaderValues(rowIterator.next(), numberColumns).toArray(new String[0]));
			} else {
				columnNames = Collections.nCopies(numberColumns, "");
			}

			final List<List<String>> samples = getAttributeSamples(rowIterator, numberColumns);
			return estimateDataConfiguration(samples, algorithm, numberColumns, columnNames);
		});
	}

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final OutputStream outputStream, final DataSet dataset) throws InternalIOException {
        var versionParts = cinnamonVersion.split("\\.");
        var version = versionParts[0] + "." + versionParts[1];

        try (final Workbook workbook = new Workbook(outputStream, "Cinnamon", version);) {
            final Worksheet worksheet = workbook.newWorksheet("dataset");

            final List<String> columnNames = dataset.getDataConfiguration().getColumnNames();
            for (int i = 0; i < columnNames.size(); i++) {
                worksheet.value(0, i, columnNames.get(i));
            }

            List<String> dateFormatter = new ArrayList<>();
            for (final ColumnConfiguration columnConfiguration : dataset.getDataConfiguration().getConfigurations()) {
                dateFormatter.add("");
                columnConfiguration.getConfigurations()
                                   .stream()
                                   .filter(configuration -> configuration.getClass()
                                                                         .equals(DateFormatConfiguration.class))
                                   .findFirst()
                                   .ifPresent(configuration -> {
                                       dateFormatter.set(dateFormatter.size() - 1,
                                                         ((DateFormatConfiguration) configuration).getDateFormatter());
                                   });

                columnConfiguration.getConfigurations()
                                   .stream()
                                   .filter(configuration -> configuration.getClass()
                                                                         .equals(DateTimeFormatConfiguration.class))
                                   .findFirst()
                                   .ifPresent(configuration -> {
                                       dateFormatter.set(dateFormatter.size() - 1,
                                                         ((DateTimeFormatConfiguration) configuration).getDateTimeFormatter());
                                   });
            }

            for (int rowIndex = 0; rowIndex < dataset.getDataRows().size(); rowIndex++) {
                final DataRow row = dataset.getDataRows().get(rowIndex);

                for (int columnIndex = 0; columnIndex < row.getRow().size(); columnIndex++) {
                    final Data data = row.getData().get(columnIndex);

                    if (data instanceof BooleanData booleanData) {
                        worksheet.value(rowIndex + 1, columnIndex, booleanData.getValue());
                    } else if (data instanceof DateData dateData) {
                        worksheet.value(rowIndex + 1, columnIndex, dateData.getValue());
                        worksheet.style(rowIndex + 1, columnIndex).format(dateFormatter.get(columnIndex)).set();
                    } else if (data instanceof DateTimeData dateTimeData) {
                        worksheet.value(rowIndex + 1, columnIndex, dateTimeData.getValue());
                        worksheet.style(rowIndex + 1, columnIndex).format(dateFormatter.get(columnIndex)).set();
                    } else if (data instanceof DecimalData decimalData) {
                        worksheet.value(rowIndex + 1, columnIndex, decimalData.getValue());
                    } else if (data instanceof IntegerData integerData) {
                        worksheet.value(rowIndex + 1, columnIndex, integerData.getValue());
                    } else if (data instanceof StringData stringData) {
                        worksheet.value(rowIndex + 1, columnIndex, stringData.getValue());
                    } else if (data instanceof TextData textData) {
                        worksheet.value(rowIndex + 1, columnIndex, textData.getValue());
                    }
                }
            }

        } catch (IOException e) {
	        throw new InternalIOException(InternalIOException.XLSX_CREATION, "Failed to create the XLSX file!", e);
        }
    }

	/**
	 * Opens the first sheet of the XLSX file and applies the given function to the stream of its rows.
	 * The rows are read lazily, so functions that do not consume all rows do not read the whole sheet.
	 *
	 * @param data     Stream containing the XLSX file.
	 * @param function The function processing the rows.
	 * @param <T>      The result type.
	 * @param <E>      Exception thrown by the function.
	 * @return The result of the function.
	 * @throws InternalIOException If reading the XLSX file failed.
	 * @throws E                   If the function failed.
	 */
	private static <T, E extends Exception> T withRows(final InputStream data, final RowsFunction<T, E> function)
			throws InternalIOException, E {
		try (final InputStream is = data;
		     final ReadableWorkbook workbook = new ReadableWorkbook(is);
		     final Stream<Row> rows = workbook.getFirstSheet().openStream()) {
			return function.apply(rows);
		} catch (final IOException | UncheckedIOException e) {
			throw new InternalIOException(InternalIOException.XLSX_READING, "Failed to read the XLSX file", e);
		}
	}

	/**
	 * Returns the number of columns, which is the width of the widest row.
	 */
	private static int getNumberColumns(final Stream<Row> rows) {
		return rows.mapToInt(XlsxProcessor::getWidth).max().orElse(0);
	}

	/**
	 * Returns the width of the row, i.e. the index of the last non-empty cell plus one.
	 * Rows can end before the last column if the trailing cells are empty.
	 */
	private static int getWidth(final Row row) {
		for (int i = row.getCellCount() - 1; i >= 0; i--) {
			final Optional<String> value = row.getCellRawValue(i);
			if (value.isPresent() && !value.get().isEmpty()) {
				return i + 1;
			}
		}
		return 0;
	}

	/**
	 * Returns the raw values of the row, padded with null values to the given width.
	 */
	private static List<@Nullable String> getRawValues(final Row row, final int width) {
		final List<@Nullable String> values = new ArrayList<>(width);
		for (int i = 0; i < width; i++) {
			values.add(i < row.getCellCount() ? row.getCellRawValue(i).orElse(null) : null);
		}
		return values;
	}

	/**
	 * Pads the values to the given width and replaces missing values by empty strings, as used for header rows.
	 */
	private static List<String> toHeaderValues(final List<@Nullable String> values, final int width) {
		final List<String> headerValues = new ArrayList<>(width);
		for (int i = 0; i < width; i++) {
			final String value = i < values.size() ? values.get(i) : null;
			headerValues.add(value != null ? value : "");
		}
		return headerValues;
	}

	private static byte[] readAllBytes(final InputStream data) throws InternalIOException {
		try (final InputStream is = data) {
			return is.readAllBytes();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.XLSX_READING, "Failed to read the XLSX file", e);
		}
	}

	/**
	 * Function processing the rows of a sheet.
	 *
	 * @param <T> The result type.
	 * @param <E> Exception thrown by the function.
	 */
	@FunctionalInterface
	private interface RowsFunction<T, E extends Exception> {
		T apply(Stream<Row> rows) throws E;
	}
}
