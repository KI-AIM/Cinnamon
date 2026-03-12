package de.kiaim.cinnamon.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Set;

@Data @With
@NoArgsConstructor @AllArgsConstructor
public class ErrorDetails {
	@Nullable
	private String configurationName;

	@Nullable
	private Map<String, Set<String>> validationErrors;

	@Schema(description = "Information about the stage that failed.")
	@Nullable
	private ExecutionStepInformation stageInfo;
}
