package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DateTimeData extends Data {

	private final LocalDateTime value;

	@Override
	public DataType getDataType() {
		return DataType.DATE_TIME;
	}
}
