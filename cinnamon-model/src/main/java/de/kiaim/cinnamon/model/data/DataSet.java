package de.kiaim.cinnamon.model.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.serialization.DataSetDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Schema(description = "A data set containing a list of rows.", example = DataSet.DATA_SET_EXAMPLE)
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonDeserialize(using = DataSetDeserializer.class)
public class DataSet {

	public final static String DATA_SET_EXAMPLE = """
	                                              {"dataConfiguration":""" +
	                                              DataConfiguration.DATA_CONFIGURATION_EXAMPLE +
	                                              """
	                                              ,"data":[""" + DataRow.DATA_ROW_EXAMPLE + "]}";

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
