package de.kiaim.platform.model.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.helper.KiAimConfigurationPostProcessor;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.enumeration.StepInputEncoding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter @Setter
public class ExternalEndpoint {

	/**
	 * Index of the server.
	 */
	@NotBlank
	private Integer externalServerIndex;

	/**
	 * Endpoint for fetching the available algorithms.
	 */
	@NotBlank
	private String algorithmEndpoint;

	/**
	 * Name of the callback url part in the request.
	 */
	private String callbackPartName = "callback";

	/**
	 * Endpoint for cancelling requests.
	 */
	@JsonIgnore
	@NotBlank
	private String cancelEndpoint;

	/**
	 * HTTP method for the cancel endpoint.
	 */
	private RequestMethod cancelHttpMethod = RequestMethod.POST;

	/**
	 * Endpoint used if used cannot select an algorithm.
	 */
	private String processEndpoint = "";

	/**
	 * Name of the configuration.
	 */
	@NotBlank
	private String configurationName;

	/**
	 * Name of the configuration part in the request.
	 */
	@NotBlank
	private String configurationPartName;

	/**
	 * Encoding of the configuration.
	 */
	@JsonIgnore
	private StepInputEncoding configurationEncoding;

	/**
	 * Input data sets.
	 */
	@JsonIgnore
	private List<StepInputConfiguration> inputs;

	/**
	 * Input data sets.
	 */
	@JsonIgnore
	private List<StepOutputConfiguration> outputs = new ArrayList<>();

	/**
	 * Maximum number of processes that are allowed to run in parallel.
	 */
	@JsonIgnore
	@NotNull
	private Integer maxParallelProcess;

	/**
	 * List of required pre-processors for this step.
	 */
	@JsonIgnore
	private List<String> preProcessors = new ArrayList<>();

	/**
	 * Endpoint for retrieving the status.
	 */
	private String statusEndpoint = "";

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * Index of the endpoint. Is automatically set at {@link KiAimConfigurationPostProcessor#assignIndices()}.
	 */
	private int index;

	private ExternalServer server;

	private Set<StepConfiguration> steps = new HashSet<>();

	public Set<Step> getStep() {
		return steps.stream().map(StepConfiguration::getStep).collect(Collectors.toSet());
	}

}
