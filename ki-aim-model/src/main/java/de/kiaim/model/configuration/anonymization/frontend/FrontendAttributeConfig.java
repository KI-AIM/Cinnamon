package de.kiaim.model.configuration.anonymization.frontend;

import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.anonymization.AttributeProtection;
import de.kiaim.model.exception.anonymization.InvalidAttributeConfigException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class FrontendAttributeConfig {
    private int index;
    private String name;
    private DataType dataType;
    private DataScale scale;
    private AttributeProtection attributeProtection;
    private String intervalSize;
    private String dateFormat;

    // List of possible values for attributes with an ordinal scale
    private String[] values;

    // Validate the attribute configuration based on its type, scale, and protection
    public void validate() throws InvalidAttributeConfigException {
        validateIntervalSize();
        validateOrdinalValues();
//        validateDateFormat();
    }

    // Validate the interval size based on attribute protection and data type
    private void validateIntervalSize() throws InvalidAttributeConfigException {
        if (attributeProtection == AttributeProtection.MASKING) {
            if (dataType == DataType.STRING || dataType == DataType.INTEGER) {
                int interval = Integer.parseInt(intervalSize);
                if (interval < 1 || interval > 1000) {
                    throw new InvalidAttributeConfigException("Interval size for MASKING must be between 1 and 1000.");
                }
            } else if (dataType == DataType.DATE) {
                List<String> allowedDateIntervals = List.of("year", "month/year", "week/year");
                if (!allowedDateIntervals.contains(intervalSize)) {
                    throw new InvalidAttributeConfigException("Invalid interval size for DATE_GENERALIZATION. Allowed values: " + allowedDateIntervals);
                }
            }
        }

        if (attributeProtection == AttributeProtection.GENERALIZATION || attributeProtection == AttributeProtection.MICRO_AGGREGATION) {
            if (dataType == DataType.INTEGER) {
                float interval = Float.parseFloat(intervalSize);
                if (interval < 1 || interval > 1000) {
                    System.out.println(index);
                    System.out.println(name);
                    System.out.println(attributeProtection);
                    System.out.println(interval);
                    throw new InvalidAttributeConfigException("Interval size for GENERALIZATION or MICRO_AGGREGATION of a INTEGER DataType must be between 1 and 1000.");
                }
            } else if (dataType == DataType.DECIMAL) {
                float interval = Float.parseFloat(intervalSize);
                if (interval < 0.001 || interval > 1000.0) {
                    throw new InvalidAttributeConfigException("Interval size for GENERALIZATION or MICRO_AGGREGATION of a DECIMAL DataType must be between 0.001 and 1000.0.");
                }
            }
        }
    }

    // Validate that values are provided for ordinal attributes
    private void validateOrdinalValues() throws InvalidAttributeConfigException {
        if (scale == DataScale.ORDINAL && (values == null || values.length == 0)) {
            throw new InvalidAttributeConfigException("Values must be provided for attributes with ORDINAL scale.");
        }
    }

    // Validate that the dateFormat is provided for DATE or DATE_TIME data types
//    private void validateDateFormat() throws InvalidAttributeConfigException {
//        if ((dataType == DataType.DATE || dataType == DataType.DATE_TIME) && (dateFormat == null || dateFormat.isEmpty())) {
//            throw new InvalidAttributeConfigException("dateFormat must be provided for DATE or DATE_TIME attributes.");
//        }
//    }
}
