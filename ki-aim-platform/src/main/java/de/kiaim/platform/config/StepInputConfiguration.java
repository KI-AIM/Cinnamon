package de.kiaim.platform.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.model.enumeration.DataSetSelector;
import de.kiaim.platform.model.enumeration.StepInputEncoding;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for one input data set.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class StepInputConfiguration {
	/**
	 * Which data set should be selected.
	 */
	private DataSetSelector selector;
	/**
	 * How the data set will be encoded for the request.
	 */
	private StepInputEncoding encoding;

	/**
	 * Name of the part in the multipart request of the data set.
	 */
	private String partName;

	/**
	 * Only used if {@link #encoding} is {@link StepInputEncoding#FILE}
	 */
	private String fileName;

	/**
	 * Only used if {@link #encoding} is {@link StepInputEncoding#FILE}
	 * Otherwise will be serialized in partName together with the data set.
	 */
	private String dataConfigurationName;
}
