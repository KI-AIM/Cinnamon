package de.kiaim.platform.model.statistics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Schema(description = "Represents the data of a histogram result")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class HistogramData {
    @Schema(description = "The collected Map of Histogram entries for all columns", example =
        """
        {
            'column1': {
                'value1': 245,
                'value2': 12,
            },
        }
        """
    )
    private Map<String, HistogramColumnData> data;

    /**
     * Puts a HistogramColumnData entry into the data map
     * @param columnName to add to the map
     * @param data for the columnName
     */
    public void putHistogramColumn(String columnName, HistogramColumnData data) {
        this.data.put(columnName, data);
    }

}
