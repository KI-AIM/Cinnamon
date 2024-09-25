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
public class HistogramColumnData {

    @Schema(description = "The histogram data for one column", example =
        """
        {
            'value1': 245,
            'value2': 12,
        }
        """
    )
    Map<String, Integer> columnData;

    /**
     * Adds a new entry to the histogram data.
     * If the value is already in the data {@link Map}, then the
     * int value is increase by 1, otherwise the data will be added
     * with a value of 1.
     * @param value to add to the {@link Map}
     */
    public void addEntry(String value) {
        if (this.columnData.containsKey(value)) {
            int currentValue = this.columnData.get(value);
            currentValue++;
            this.columnData.put(value, currentValue);
        } else {
            this.columnData.put(value, 1);
        }
    }
}
