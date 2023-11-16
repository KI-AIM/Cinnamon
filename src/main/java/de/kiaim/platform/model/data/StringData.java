package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StringData extends Data {

	private final String value;

	@Override
	public DataType getDataType() {
		return DataType.STRING;
	}
}
