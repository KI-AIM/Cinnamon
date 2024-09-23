package de.kiaim.platform.model.dto;

import de.kiaim.model.data.DataRow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Schema(description = "Page of a data set.")
@Getter @AllArgsConstructor
public class DataSetPage {

	@Schema(description = "Row of the data set.", implementation = DataRow.class)
	private final List<List<Object>> data;

	@Schema(description = "Number of the returned page starting at 1.", example = "3")
	private final int page;

	@Schema(description = "Number of items per page.", example = "10")
	private final int perPage;

	@Schema(description = "Total number of available items.", example = "274")
	private final int total;

	@Schema(description = "Total number of pages.", example = "28")
	private final int totalPages;
}
