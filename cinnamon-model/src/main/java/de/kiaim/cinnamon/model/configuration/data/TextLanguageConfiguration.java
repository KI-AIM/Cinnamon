package de.kiaim.cinnamon.model.configuration.data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Schema(description = "Describes the language of free text content.",
        example = "{\"name\": \"TextLanguageConfiguration\", \"language\": \"ENGLISH\"}")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class TextLanguageConfiguration implements Configuration {

	@Schema(description = "Language of the text.", example = "ENGLISH")
	@NotBlank(message = "The language must not be empty!")
	@Pattern(regexp = "^(ENGLISH|GERMAN|FRENCH|SPANISH|ITALIAN|PORTUGUESE)$",
	         message = "Unsupported language! Supported values: ENGLISH, GERMAN, FRENCH, SPANISH, ITALIAN, PORTUGUESE")
	String language;
}
