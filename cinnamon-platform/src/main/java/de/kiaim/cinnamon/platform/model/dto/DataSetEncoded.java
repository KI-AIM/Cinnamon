package de.kiaim.cinnamon.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Result of requesting a dataset.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Result of requesting a dataset.", example = DataSet.DATA_SET_EXAMPLE)
@Getter
@AllArgsConstructor
public class DataSetEncoded {
	@Schema(description = "Metadata of the columns.")
	private final DataConfiguration dataConfiguration;

	@Schema(description = "The data encoded as specified in request.")
	private final List<List<@Nullable Object>> data;

	@Schema(description = "Transformation errors of the data, grouped by row. " +
	                      "The indices are the positions of the row and column in the data. " +
	                      "Not present if the errors were not requested.")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Nullable
	private final List<DataRowTransformationError> transformationErrors;
}
