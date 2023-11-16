package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BooleanData extends Data {

	private final Boolean value;

	@Override
	public DataType getDataType() {
		return DataType.BOOLEAN;
	}
}
