package de.kiaim.model.configuration.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Empty interface that all Data configurations
 * should implement in order to be dynamically
 * processed
 */
@Schema(description = "Interface for different configurations.",
        anyOf = {DateFormatConfiguration.class, DateTimeFormatConfiguration.class, RangeConfiguration.class,
                 StringPatternConfiguration.class})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "name")
@JsonSubTypes({
		@JsonSubTypes.Type(DateFormatConfiguration.class),
		@JsonSubTypes.Type(DateTimeFormatConfiguration.class),
		@JsonSubTypes.Type(RangeConfiguration.class),
		@JsonSubTypes.Type(StringPatternConfiguration.class),
})
public interface Configuration {
}
