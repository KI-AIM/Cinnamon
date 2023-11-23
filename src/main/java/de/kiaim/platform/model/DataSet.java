package de.kiaim.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.kiaim.platform.model.data.Data;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DataSet {

	@JsonIgnore
	final List<DataRow> dataRows;

	final DataConfiguration dataConfiguration;

	@JsonProperty("data")
	final List<List<Object>> getData() {
		return dataRows.stream()
		               .map(dataRow -> dataRow.getData()
		                                      .stream()
		                                      .map(Data::getValue)
		                                      .toList())
		               .toList();
	}
}
