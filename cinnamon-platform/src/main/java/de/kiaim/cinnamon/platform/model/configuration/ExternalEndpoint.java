package de.kiaim.cinnamon.platform.model.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.platform.model.enumeration.StepInputEncoding;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class ExternalEndpoint {

	/**
	 * Name of the server.
	 */
	@NotBlank
	private String externalServerName;

	/**
	 * Name of the callback url part in the request.
	 */
	private String callbackPartName = "callback";

	/**
	 * Endpoint for cancelling requests.
	 */
	@JsonIgnore
	private String cancelEndpoint = "";

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
	 * Index of the endpoint.
	 */
	private int index;

	/**
	 * Server used for this endpoint.
	 */
	private ExternalServer server;

	/**
	 * Configuration required by this endpoint.
	 * Mapping for {@link ExternalConfiguration#getUsages()}.
	 */
	private ExternalConfiguration configuration;

	/**
	 * List of jobs that use this endpoint.
	 * Mapping for {@link Job#getEndpoint()}.
	 */
	private List<Job> usages = new ArrayList<>();
}
