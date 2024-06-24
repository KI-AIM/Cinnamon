package de.kiaim.model.configuration.data;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "Describes the format of string.",
        example = "{\"name\": \"StringPatternConfiguration\", \"dataFormatter\": \".*\"}")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class StringPatternConfiguration implements Configuration {

    /**
     * The Regex that a string should match
     */
    @Schema(description = "RegEx pattern describing the strings.", example = ".*")
    String pattern;
}
