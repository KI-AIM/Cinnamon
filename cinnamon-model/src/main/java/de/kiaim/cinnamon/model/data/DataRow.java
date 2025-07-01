package de.kiaim.cinnamon.model.data;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

@ArraySchema(schema = @Schema(implementation = Data.class, example = DataRow.DATA_ROW_EXAMPLE))
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DataRow {

	public final static String DATA_ROW_EXAMPLE = "[true, \"2023-12-24\", \"2023-12-24T18:30:01.123456\", 4.2, 42, \"Hello World!\"]";

	private final List<Data> data;

	public final List<Object> getRow() {
		return data.stream().map(Data::getValue).collect(Collectors.toList());
	}
}
