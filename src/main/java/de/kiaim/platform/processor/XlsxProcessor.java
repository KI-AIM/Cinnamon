package de.kiaim.platform.processor;

import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.file.XlsxFileConfiguration;
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
            rows = transformSheetToRows(sheet);

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

    private List<List<String>> transformSheetToRows(Sheet sheet) {
        List<List<String>> convertedRows = new ArrayList<>();

        try (Stream<Row> rows = sheet.openStream()) {
            rows.forEach(r -> {
                List<String> convertedRow = new ArrayList<>();

                try (Stream<Cell> cells = r.stream()) {
                    cells.forEach(c -> {
                        convertedRow.add(c.getRawValue());
                    });
                }
                convertedRows.add(convertedRow);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return convertedRows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfiguration estimateDatatypes(InputStream data, FileConfiguration fileConfiguration) {
        final XlsxFileConfiguration xlsxFileConfiguration = fileConfiguration.getXlsxFileConfiguration();
        List<List<String>> rows;

        try (InputStream is = data; ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            rows = transformSheetToRows(sheet);

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

        final List<DataType> estimatedDataTypes;
        if (validRows.isEmpty()) {
            estimatedDataTypes = getUndefinedDatatypesList(numberColumns);
        } else {
            estimatedDataTypes = estimateDatatypesForMultipleRows(validRows);
        }

        return buildConfigurationForDataTypes(estimatedDataTypes, columnNames);
    }

    /**
     * Function that returns a subset of complete rows for xlsx records.
     * Complete means that no missing value should be present in a row.
     * The amount of rows is limited by the parameter maxNumberOfRows.
     *
     * @param rows List structure that holds the rows
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
