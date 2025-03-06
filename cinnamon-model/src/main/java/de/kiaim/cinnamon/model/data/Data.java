package de.kiaim.cinnamon.model.data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.serialization.DataDeserializer;
import de.kiaim.cinnamon.model.serialization.DataSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(anyOf = {Boolean.class, LocalDate.class, LocalDateTime.class, Float.class, Integer.class, String.class},
        examples = {"true", "\"2023-12-24\"", "\"2023-12-24 18:30:01\"", "4.2", "42", "\"Hello World!\""})
@JsonDeserialize(using = DataDeserializer.class)
@JsonSerialize(using = DataSerializer.class)
public abstract class Data {

	@Schema(hidden = true)
	public abstract DataType getDataType();

	@Schema(hidden = true)
	@Nullable
	public abstract Object getValue();

	@Nullable
	public Boolean asBoolean() {
		return (Boolean) getValue();
	}

	@Nullable
	public LocalDate asDate() {
		return (LocalDate) getValue();
	}

	@Nullable
	public LocalDateTime asDateTime() {
		return (LocalDateTime) getValue();
	}

	@Nullable
	public Float asDecimal() {
		return (Float) getValue();
	}

	@Nullable
	public Integer asInteger() {
		return (Integer) getValue();
	}

	@Nullable
	public String asString() {
		return (String) getValue();
	}

	@Override
	public String toString() {
		final var value = getValue();
		if (value == null) {
			return "";
		}
		return value.toString();
	}
}
