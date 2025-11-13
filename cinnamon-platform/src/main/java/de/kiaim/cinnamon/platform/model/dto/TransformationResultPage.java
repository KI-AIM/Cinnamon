package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Schema(description = "Page of a transformation result.")
@Getter @AllArgsConstructor
public class TransformationResultPage {

	@Schema(description = "Row of the data set.", implementation = DataRow.class)
	private final List<List<Object>> data;

	@Schema(description = "List of all transformation errors.")
	private final List<DataRowTransformationError> transformationErrors;

	@Schema(description = "Row numbers for the elements of the data field starting at 0.", example = "[20, 21, 22]")
	private final List<Integer> rowNumbers;

	@Schema(description = "Number of the returned page starting at 1.", example = "3")
	private final int page;

	@Schema(description = "Number of items per page.", example = "10")
	private final int perPage;

	@Schema(description = "Total number of available items.", example = "274")
	private final int total;

	@Schema(description = "Total number of pages.", example = "28")
	private final int totalPages;
}
