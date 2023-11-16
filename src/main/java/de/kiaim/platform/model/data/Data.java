package de.kiaim.platform.model.data;

import java.time.LocalDateTime;

public abstract class Data {

	public abstract DataType getDataType();

	public abstract Object getValue();

	public Boolean asBoolean() {
		return (Boolean) getValue();
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
