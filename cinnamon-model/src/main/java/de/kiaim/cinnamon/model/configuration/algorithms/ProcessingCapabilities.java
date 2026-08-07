package de.kiaim.cinnamon.model.configuration.algorithms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Processing capabilities of an externally provided algorithm.
 */
@Schema(description = "Processing capabilities of an externally provided algorithm.")
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
@EqualsAndHashCode
public class ProcessingCapabilities {
	@Schema(description = "Input data modality supported by the algorithm.", example = "mixed")
	@JsonProperty("data_modality")
	private String dataModality;

	@Schema(description = "Which part of the data the algorithm generates.", example = "text_only")
	@JsonProperty("generation_scope")
	private String generationScope;

	@Schema(description = "Legacy flag indicating support for structured data.", example = "true")
	@JsonProperty("supports_structured_data")
	private Boolean supportsStructuredData;

	@Schema(description = "Legacy flag indicating support for free-text data.", example = "false")
	@JsonProperty("supports_free_text_data")
	private Boolean supportsFreeTextData;
}
