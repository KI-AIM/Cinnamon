package de.kiaim.cinnamon.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Set;

@Data @With
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetails {
	@Nullable
	private String configurationName;

	@Nullable
	private Map<String, Set<String>> validationErrors;

	@Schema(description = "Information about the stage that failed.")
	@Nullable
	private ExecutionStepInformation stageInfo;

	@Nullable
	private ConfigurationImportSummary configurationImportSummary;
}
