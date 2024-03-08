package de.kiaim.platform.model.data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.kiaim.platform.json.DataDeserializer;
import de.kiaim.platform.json.DataSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
	public abstract Object getValue();

	public Boolean asBoolean() {
		return (Boolean) getValue();
	}

	public LocalDate asDate() {
		return (LocalDate) getValue();
	}

	public LocalDateTime asDateTime() {
		return (LocalDateTime) getValue();
	}

	public Float asDecimal() {
		return (Float) getValue();
	}

	public Integer asInteger() {
		return (Integer) getValue();
	}

	public String asString() {
		return (String) getValue();
	}
}
