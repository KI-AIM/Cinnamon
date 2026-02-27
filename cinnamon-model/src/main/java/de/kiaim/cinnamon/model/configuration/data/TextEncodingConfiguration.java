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

@Schema(description = "Describes the encoding of free text content.",
        example = "{\"name\": \"TextEncodingConfiguration\", \"encoding\": \"UTF-8\"}")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class TextEncodingConfiguration implements Configuration {

	@Schema(description = "Encoding of the text.", example = "UTF-8")
	@NotBlank(message = "The encoding must not be empty!")
	@Pattern(regexp = "^(UTF-8|UTF-16|ISO-8859-1|WINDOWS-1252|ASCII)$",
	         message = "Unsupported encoding! Supported values: UTF-8, UTF-16, ISO-8859-1, WINDOWS-1252, ASCII")
	String encoding;
}
