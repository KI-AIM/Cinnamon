package de.kiaim.cinnamon.model.status.synthetization;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SynthetizationComponentStatus {

	private String completed;

	private String duration;

	@JsonProperty(value = "fitting_duration")
	private String fittingDuration;

	@JsonProperty(value = "initialization_duration")
	private String initializationDuration;

	@JsonProperty(value = "remaining_time")
	private String remainingTime;

	@JsonProperty(value = "sampling_duration")
	private String samplingDuration;

	@JsonProperty(value = "synthesizer_name")
	private String synthesizerName;
}
