package de.kiaim.cinnamon.model.status.synthetization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public class SynthetizationComponentStatus {

	private String completed;

	@Nullable
	private String duration;

	@JsonProperty(value = "fitting_duration")
	@Nullable
	private String fittingDuration;

	@JsonProperty(value = "fitting_remaining_time")
	@Nullable
	private String fittingRemainingTime;

	@JsonProperty(value = "initialization_duration")
	@Nullable
	private String initializationDuration;

	@JsonProperty(value = "remaining_time")
	@Nullable
	private String remainingTime;

	@JsonProperty(value = "sampling_duration")
	@Nullable
	private String samplingDuration;

	@JsonProperty(value = "sampling_remaining_time")
	@Nullable
	private String samplingRemainingTime;

	@Nullable
	private String step;

	@JsonProperty(value = "synthesizer_name")
	@Nullable
	private String synthesizerName;
}
