package de.kiaim.model.status.synthetization;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class SynthetizationStatus {

	@JsonProperty(value = "session_key")
	private String sessionKey;

	private List<SynthetizationStepStatus> status;

	@JsonProperty(value = "synthesizer_name")
	private String synthesizerName;
}
