package de.kiaim.cinnamon.model.status.synthetization;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter @Setter
public class SynthetizationStatus {

	private Map<String, SynthetizationComponentStatus> components;

	@JsonProperty(value = "session_key")
	private String sessionKey;

	private List<SynthetizationStepStatus> status;

	@JsonProperty(value = "synthesizer_name")
	private String synthesizerName;
}
