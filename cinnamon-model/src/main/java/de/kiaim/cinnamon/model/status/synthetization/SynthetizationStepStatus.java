package de.kiaim.cinnamon.model.status.synthetization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public class SynthetizationStepStatus {

	private String completed;

	@Nullable
	private String duration;

	@JsonProperty(value = "remaining_time")
	@Nullable
	private String remainingTime;

	private String step;
}
