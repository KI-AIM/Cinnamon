package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DecimalData extends Data {

	private final Float value;

	@Override
	public DataType getDataType() {
		return DataType.DECIMAL;
	}
}
