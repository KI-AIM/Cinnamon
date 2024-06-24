package de.kiaim.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.kiaim.model.data.Data;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.json.DataSetSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonSerialize(using = DataSetSerializer.class)
public class DataSet {

	@JsonIgnore
	final List<DataRow> dataRows;

	@Schema(description = "Metadata of the data")
	@Getter
	final DataConfiguration dataConfiguration;

	@ArraySchema(schema = @Schema(description = "Row of the data set.", implementation = DataRow.class))
	public final List<List<Object>> getData() {
		return dataRows.stream()
		               .map(dataRow -> dataRow.getData()
		                                      .stream()
		                                      .map(Data::getValue)
		                                      .collect(Collectors.toList()))
		               .toList();
	}
}
