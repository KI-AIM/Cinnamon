package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.ImportConfigurationDTO;
import de.kiaim.cinnamon.model.dto.AttributeMatchingResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class AttributeImportOverview {

	/**
	 * The import configuration stored in the database.
	 */
	private final ImportConfigurationDTO importConfiguration;

	/**
	 * The confidences of the estimation for each column ordered by the attribute index.
	 */
	private final float[] confidences;

	List<AttributeMatchingResult> attributeMatchingResults;

//	private final


}
