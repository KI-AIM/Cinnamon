package de.kiaim.platform.model.configuration;

import de.kiaim.platform.model.enumeration.StepOutputEncoding;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StepOutputConfiguration {

	/**
	 * Name of the part in the multipart request of the data set.
	 */
	private String partName;

	/**
	 * The type of the output.
	 */
	private StepOutputEncoding encoding;
}
