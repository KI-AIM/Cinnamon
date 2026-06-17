package de.kiaim.cinnamon.model.status.synthetization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public class SynthetizationStatus {

	@JsonSetter(nulls = Nulls.AS_EMPTY)
	private Map<String, SynthetizationComponentStatus> components = new HashMap<>();

	@JsonProperty(value = "session_key")
	private String sessionKey;

	@JsonSetter(nulls = Nulls.AS_EMPTY)
	private List<SynthetizationStepStatus> status = new ArrayList<>();

	@JsonProperty(value = "synthesizer_name")
	private String synthesizerName;
}
