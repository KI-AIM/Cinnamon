package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Schema(description = "A data set containing a list of rows.", example = DataSet.DATA_SET_EXAMPLE)
@Getter
@AllArgsConstructor
public class DataSetEncoded {
	@Schema(description = "Metadata of the data")
	private final DataConfiguration dataConfiguration;

	@Schema(description = "The data")
	private final List<List<Object>> data;
}
