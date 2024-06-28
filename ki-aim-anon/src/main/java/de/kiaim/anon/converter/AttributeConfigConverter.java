package de.kiaim.anon.converter;

import de.kiaim.model.data.Data;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import org.bihmi.jal.config.AttributeConfig;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Convert KI-AIM AttributeConfig into JAL AttributeConfig.
 * Based on the AnonymizationConfig file and DataSet, some JAL attributes are automatically generated.
 */
public class AttributeConfigConverter {
    /**
     * Converts a KI-AIM specific AttributeConfig to a general AttributeConfig, extracting min and max values if applicable.
     *
     * @param kiAttributeConfig The KI-AIM specific AttributeConfig to be converted.
     * @param dataSet The dataset from which to extract min and max values for numeric columns.
     * @return The converted AttributeConfig.
     */
    public static AttributeConfig convert(
            de.kiaim.model.configuration.anonymization.AttributeConfig kiAttributeConfig,
            DataSet dataSet) {

        Object minValue = null;
        Object maxValue = null;

        List<DataRow> rows = dataSet.getDataRows();
        List<Data> columnData = rows.stream()
                .map(row -> row.getData().get(kiAttributeConfig.getIndex()))
                .toList();

//        switch (kiAttributeConfig.getDataType()) {
//            case DECIMAL:
//                minValue = getMinValue(columnData, DecimalData.class, DecimalData::getValue, Float::compare);
//                maxValue = getMaxValue(columnData, DecimalData.class, DecimalData::getValue, Float::compare);
//                break;
//            case INTEGER:
//                minValue = getMinValue(columnData, IntegerData.class, IntegerData::getValue, Integer::compare);
//                maxValue = getMaxValue(columnData, IntegerData.class, IntegerData::getValue, Integer::compare);
//                break;
//            // TODO : handle date (?)
//        }

        AttributeConfig.AttributeConfigBuilder builder = AttributeConfig.builder()
                .name(kiAttributeConfig.getName())
                .dataType(kiAttributeConfig.getDataType().toString())
                .attributeType(kiAttributeConfig.getAttributeType())
                .dateFormat(kiAttributeConfig.getDateFormat())
                .hierarchyConfig(HierarchyConverter.convert(kiAttributeConfig.getHierarchy(), kiAttributeConfig.getName()));

        if (minValue != null) {
            builder.min(minValue);
        }
        if (maxValue != null) {
            builder.max(maxValue);
        }

        return builder.build();
    }


    /**
     * Extracts the minimum value from a list of Data objects.
     *
     * @param columnData The list of Data objects.
     * @param type The class type of the Data objects to filter.
     * @param valueExtractor A function to extract the value from the Data objects.
     * @param comparator A comparator to compare the extracted values.
     * @param <T> The type of Data objects.
     * @param <U> The type of the extracted values.
     * @return The minimum value, or null if no values are found.
     */
    private static <T extends Data, U> U getMinValue(List<Data> columnData, Class<T> type, Function<T, U> valueExtractor, Comparator<U> comparator) {
        return columnData.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .map(valueExtractor)
                .min(comparator)
                .orElse(null);
    }

    /**
     * Extracts the maximum value from a list of Data objects.
     *
     * @param columnData The list of Data objects.
     * @param type The class type of the Data objects to filter.
     * @param valueExtractor A function to extract the value from the Data objects.
     * @param comparator A comparator to compare the extracted values.
     * @param <T> The type of Data objects.
     * @param <U> The type of the extracted values.
     * @return The maximum value, or null if no values are found.
     */
    private static <T extends Data, U> U getMaxValue(List<Data> columnData, Class<T> type, Function<T, U> valueExtractor, Comparator<U> comparator) {
        return columnData.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .map(valueExtractor)
                .max(comparator)
                .orElse(null);
    }

    public static List<AttributeConfig> convertList(
            List<de.kiaim.model.configuration.anonymization.AttributeConfig> kiAttributeConfigs,
            DataSet dataSet) {
        return kiAttributeConfigs.stream()
                .map(kiAttributeConfig -> convert(kiAttributeConfig, dataSet))
                .collect(Collectors.toList());
    }
}
