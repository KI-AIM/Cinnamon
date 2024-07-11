package de.kiaim.model.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.serialization.DataSetDeserializer;
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
@JsonDeserialize(using = DataSetDeserializer.class)
public class DataSet {

	@JsonIgnore
	final List<DataRow> dataRows;

	@Schema(description = "Metadata of the data")
	@Getter
	final DataConfiguration dataConfiguration;

	@ArraySchema(schema = @Schema(description = "Row of the data set.", implementation = DataRow.class))
	public final List<List<Object>> getData() {
		return dataRows.stream().map(DataRow::getRow).toList();
	}

	public void setData(final List<List<Data>> data) {
		for (final List<Data> row : data) {
			dataRows.add(new DataRow(row));
		}
	}
}
