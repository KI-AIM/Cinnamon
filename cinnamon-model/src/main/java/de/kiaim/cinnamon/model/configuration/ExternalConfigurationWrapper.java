package de.kiaim.cinnamon.model.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wraps a configuration as a single dynamically named YAML/JSON property.
 *
 * <p>Using {@link java.util.Map.Entry} directly does not produce a mapping
 * whose key is the configuration name. Jackson would instead serialize the
 * entry as {@code key: ...} and {@code value: ...}. This type exposes the
 * desired mapping through {@link JsonAnyGetter}.</p>
 */
public final class ExternalConfigurationWrapper implements ConfigurationDTO {

	private final String name;
	private final ConfigurationPart value;

	public ExternalConfigurationWrapper(final String name, final ConfigurationPart value) {
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.value = value;
	}

	/**
	 * Creates a wrapper from its single dynamically named property.
	 *
	 * @param configurations the dynamically named configuration property.
	 * @throws IllegalArgumentException if the input does not contain exactly one property.
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public ExternalConfigurationWrapper(final Map<String, ConfigurationPart> configurations) {
		if (configurations == null || configurations.size() != 1) {
			throw new IllegalArgumentException("Exactly one configuration property is required");
		}

		final Map.Entry<String, ConfigurationPart> configuration = configurations.entrySet().iterator().next();
		this.name = Objects.requireNonNull(configuration.getKey(), "name must not be null");
		this.value = configuration.getValue();
	}

	@JsonAnyGetter
	public Map<String, ConfigurationPart> asMap() {
		final Map<String, ConfigurationPart> map = new LinkedHashMap<>();
		map.put(name, value);
		return map;
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public String getKey() {
		return name;
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public boolean includesKey() {
		return true;
	}

}
