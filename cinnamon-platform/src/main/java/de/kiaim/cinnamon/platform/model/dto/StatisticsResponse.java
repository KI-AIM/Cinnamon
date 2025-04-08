package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Schema(description = "Response when asking for statistics.")
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
public class StatisticsResponse {

	@Schema(description = "The status of the statistics calculation.")
	private ProcessStatus status;

	@Nullable
	@Schema(description = "Result string containing the statistics. Is null if status is not FINISHED.")
	private String statistics;
}
