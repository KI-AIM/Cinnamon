package de.kiaim.platform.service;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.data.Data;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.platform.model.statistics.HistogramColumnData;
import de.kiaim.platform.model.statistics.HistogramData;
import java.util.TreeMap;
import org.springframework.stereotype.Service;


@Service
public class StatisticsService {
    public HistogramData calculateHistogram(DataSet data) {
        HistogramData histogramData = initHistogramDataHolderForDataset(data);

        for(DataRow row: data.getDataRows()) {
            for (int i = 0; i < row.getData().size(); i++) {
                Data columnData = row.getData().get(i);
                String columnName = data.getDataConfiguration().getColumnNames().get(i);
                HistogramColumnData histogramColumnData = histogramData.getData().get(columnName);
                histogramColumnData.addEntry(columnData.toString());
            }
        }

        return histogramData;
    }


    private HistogramData initHistogramDataHolderForDataset(DataSet data) {
        HistogramData histogramData = new HistogramData(new TreeMap<>());

        for (ColumnConfiguration config: data.getDataConfiguration().getConfigurations()) {
            HistogramColumnData columnData = new HistogramColumnData(new TreeMap<>());
            histogramData.putHistogramColumn(config.getName(), columnData);
        }
        return histogramData;
    }
}
