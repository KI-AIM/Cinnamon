package de.kiaim.cinnamon.anonymization.processor;

import de.kiaim.cinnamon.anonymization.exception.processor.JALDataGenerationException;
import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.model.enumeration.DataType;
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
                        if ((column.getScale() == DataScale.NOMINAL) || (column.getScale() == DataScale.ORDINAL)) {
                            content = "*";
                        }
                        else {
                            content = "NULL";
                        }
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
