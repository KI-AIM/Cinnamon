package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.*;
import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.XlsxFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.FileType;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

@Service
public class XlsxProcessor extends CommonDataProcessor implements DataProcessor{

    private final String cinnamonVersion;

    public XlsxProcessor(@Value("${cinnamon.version}") final String cinnamonVersion) {
        this.cinnamonVersion = cinnamonVersion;
    }

	@Override
    public FileType getSupportedDataType() {
        return FileType.XLSX;
    }

    @Override
    public int getNumberColumns(InputStream data, FileConfigurationEntity fileConfiguration) {
        final XlsxFileConfigurationEntity xlsxFileConfiguration = (XlsxFileConfigurationEntity) fileConfiguration;
        List<List<String>> rows;

        try(InputStream is = data; ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            rows = transformSheetToRows(sheet, getStringDataConfiguration(sheet));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rows.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransformationResult read(InputStream data, FileConfigurationEntity fileConfiguration,
                                     DataConfiguration configuration) {

        final XlsxFileConfigurationEntity xlsxFileConfiguration = (XlsxFileConfigurationEntity) fileConfiguration;
        List<List<String>> rows;

        try (InputStream is = data; ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            rows = transformSheetToRows(sheet, configuration);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!rows.isEmpty() && xlsxFileConfiguration.getHasHeader()) {
            rows.remove(0);
        }


        final List<DataRow> dataRows = new ArrayList<>();
        final List<DataRowTransformationError> errors = new ArrayList<>();
        int rowIndex = 0;

        for (List<String> row : rows) {
            transformRow(row, rowIndex, configuration, dataRows, errors);
            rowIndex += 1;
        }

        return new TransformationResult(new DataSet(dataRows, configuration), errors);
    }

    private List<List<String>> transformSheetToRows(Sheet sheet, DataConfiguration configuration) {
        List<List<String>> convertedRows = new ArrayList<>();

        try (Stream<Row> rows = sheet.openStream()) {
            rows.forEach(r -> {
                List<String> convertedRow = new ArrayList<>();

                try (Stream<Cell> cells = r.stream()) {

                    for (int i = 0; i < r.getCellCount(); i++) {
                         ColumnConfiguration columnConfiguration = configuration.getConfigurations().get(i);

                        convertedRow.add(getConvertedCellValue(r, i, columnConfiguration));
                    }
                }
                convertedRows.add(convertedRow);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return convertedRows;
    }

    private String getConvertedCellValue(Row r, int index, ColumnConfiguration columnConfiguration) {

        if (index < r.getCellCount()) {
            switch (columnConfiguration.getType()) {
                case DATE -> {
                    try {
                        LocalDateTime date = r.getCellAsDate(index).orElse(null);
                        if (date != null) {
                            List<Configuration> dateFormatConfigurations =
                                columnConfiguration.getConfigurations().stream().filter(
                                    configuration -> configuration.getClass().equals(
                                        DateFormatConfiguration.class
                                    )
                                ).toList();

                            DateFormatConfiguration dateFormatConfiguration;
                            if (!dateFormatConfigurations.isEmpty()) {
                                dateFormatConfiguration =
                                    (DateFormatConfiguration) dateFormatConfigurations.get(0);
                            } else {
                                dateFormatConfiguration = null;
                            }

                            if (dateFormatConfiguration != null) {
                                return date.format(DateTimeFormatter.ofPattern(
                                    dateFormatConfiguration.getDateFormatter()));
                            } else {
                                return date.format(DateTimeFormatter.ISO_DATE);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                case DATE_TIME -> {
                    try {
                        LocalDateTime date = r.getCellAsDate(index).orElse(null);
                        if (date != null) {
                            List<Configuration> dateTimeFormatConfigurations =
                                columnConfiguration.getConfigurations().stream().filter(
                                    configuration -> configuration.getClass().equals(
                                        DateTimeFormatConfiguration.class
                                    )
                                ).toList();

                            DateTimeFormatConfiguration dateTimeFormatConfiguration;
                            if (!dateTimeFormatConfigurations.isEmpty()) {
                                dateTimeFormatConfiguration =
                                    (DateTimeFormatConfiguration) dateTimeFormatConfigurations.get(0);
                            } else {
                                dateTimeFormatConfiguration = null;
                            }

                            if (dateTimeFormatConfiguration != null) {
                                return date.format(DateTimeFormatter.ofPattern(
                                    dateTimeFormatConfiguration.getDateTimeFormatter()));
                            } else {
                                return date.format(DateTimeFormatter.ISO_DATE);
                            }
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
                case STRING, BOOLEAN -> {
                    return r.getCellRawValue(index).orElse(null);
                }
            }
        }
        return r.getCellRawValue(index).orElse(null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfiguration estimateDataConfiguration(InputStream data, FileConfigurationEntity fileConfiguration,
                                                       final DatatypeEstimationAlgorithm algorithm) {
        final XlsxFileConfigurationEntity xlsxFileConfiguration = (XlsxFileConfigurationEntity) fileConfiguration;
        List<List<String>> rows;

        try (InputStream is = data; ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            rows = transformSheetToRows(sheet, getStringDataConfiguration(sheet));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (rows.isEmpty()) {
            return new DataConfiguration();
        }

        int numberColumns = 0;
        final List<String> columnNames;

        if (xlsxFileConfiguration.getHasHeader()) {
            columnNames = normalizeColumnNames(rows.get(0).toArray(new String[0]));
            numberColumns = columnNames.size();
        } else {
            numberColumns = rows.get(0).size();
            columnNames = Collections.nCopies(numberColumns, "");
        }

        List<List<String>> samples = getAttributeSamples(rows.iterator(), numberColumns);
        return estimateDataConfiguration(samples, algorithm, numberColumns, columnNames);
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
                    }
                }
            }

        } catch (IOException e) {
	        throw new InternalIOException(InternalIOException.XLSX_CREATION, "Failed to create the XLSX file!", e);
        }
    }

    private DataConfiguration getStringDataConfiguration(Sheet sheet) {
        DataConfiguration configuration = new DataConfiguration();

        try (Stream<Row> rows = sheet.openStream()) {
            Row firstRow = rows.toList().get(0);

            for (int i = 0; i < firstRow.getCellCount(); i++) {
                configuration.addColumnConfiguration(
                    new ColumnConfiguration(i, "column" + i, DataType.STRING, DataScale.NOMINAL, new ArrayList<>())
                );
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return configuration;
    }
}
