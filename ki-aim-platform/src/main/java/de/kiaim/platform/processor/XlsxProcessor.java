package de.kiaim.platform.processor;

import de.kiaim.model.configuration.data.*;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.file.FileType;
import de.kiaim.platform.model.file.XlsxFileConfiguration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

@Service
public class XlsxProcessor extends CommonDataProcessor implements DataProcessor{

    @Override
    public FileType getSupportedDataType() {
        return FileType.XLSX;
    }

    @Override
    public int getNumberColumns(InputStream data, FileConfiguration fileConfiguration) {
        final XlsxFileConfiguration xlsxFileConfiguration = fileConfiguration.getXlsxFileConfiguration();
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
    public TransformationResult read(InputStream data, FileConfiguration fileConfiguration,
                                     DataConfiguration configuration) {

        final XlsxFileConfiguration xlsxFileConfiguration = fileConfiguration.getXlsxFileConfiguration();
        List<List<String>> rows;

        try (InputStream is = data; ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            rows = transformSheetToRows(sheet, configuration);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!rows.isEmpty() && xlsxFileConfiguration.isHasHeader()) {
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
    public DataConfiguration estimateDatatypes(InputStream data, FileConfiguration fileConfiguration,
                                               final DatatypeEstimationAlgorithm algorithm) {
        final XlsxFileConfiguration xlsxFileConfiguration = fileConfiguration.getXlsxFileConfiguration();
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

        if (xlsxFileConfiguration.isHasHeader()) {
            columnNames = normalizeColumnNames(rows.get(0).toArray(new String[0]));
            numberColumns = columnNames.size();
        } else {
            numberColumns = rows.get(0).size();
            columnNames = Collections.nCopies(numberColumns, "");
        }

        List<String[]> validRows = getSubsetOfCompleteRows(rows, 10);
        return estimateDataConfiguration(validRows, algorithm, numberColumns, columnNames);
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

    /**
     * Function that returns a subset of complete rows for xlsx records. Complete means that no
     * missing value should be present in a row. The amount of rows is limited by the parameter
     * maxNumberOfRows.
     *
     * @param rows            List structure that holds the rows
     * @param maxNumberOfRows the maximum number of rows
     * @return A List<String[]> of split rows
     */
    private List<String[]> getSubsetOfCompleteRows(List<List<String>> rows, int maxNumberOfRows) {
        List<String[]> validRows = new ArrayList<>();

        int i = 0;
        while (i < rows.size() && validRows.size() < maxNumberOfRows) {
            List<String> row = rows.get(i);

            if (isColumnListComplete(row.toArray(new String[0]))) {
                validRows.add(row.toArray(new String[0]));
            }

            i += 1;
        }

        return validRows;
    }
}
