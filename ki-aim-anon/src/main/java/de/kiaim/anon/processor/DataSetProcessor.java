package de.kiaim.anon.processor;

import de.kiaim.anon.exception.processor.JALDataGenerationException;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to build JAL object from KI-AIM Anon DataSet object.
 */
@Service
public class DataSetProcessor {

    public String[][] convertDatasetToStringArray(DataSet dataSet){
        List<String[]> rowsList = new ArrayList<>();

        String[] headerRow = dataSet.getDataConfiguration().getColumnNames().toArray(new String[0]);
        rowsList.add(headerRow);

        try {
            for (DataRow row: dataSet.getDataRows()) {
                List<String> rowList = new ArrayList<>();
                for (ColumnConfiguration column : dataSet.getDataConfiguration().getConfigurations()) {
                    Object content = row.getData().get(column.getIndex()).getValue();
                    if (content == null) {
                        content = "NULL";
                    }
                    rowList.add(content.toString());
                }
                rowsList.add(rowList.toArray(new String[0]));
            }
        } catch (Exception e) {
            throw new JALDataGenerationException(" error : "+ e);
        }
        return rowsList.toArray(new String[0][]);
    }
}
