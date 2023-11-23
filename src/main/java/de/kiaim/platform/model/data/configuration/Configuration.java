package de.kiaim.platform.model.data.configuration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;

/**
 * Empty interface that all Data configurations
 * should implement in order to be dynamically
 * processed
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "name")
@JsonSubTypes({
		@JsonSubTypes.Type(DateFormatConfiguration.class),
		@JsonSubTypes.Type(DateTimeFormatConfiguration.class),
		@JsonSubTypes.Type(StringPatternConfiguration.class),
})
public interface Configuration {
}
