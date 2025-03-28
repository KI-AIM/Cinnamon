package de.kiaim.cinnamon.model.configuration.data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Schema(description = "Describes the format of a date time.",
        example = "{\"name\": \"DateTimeFormatConfiguration\", \"dataFormatter\": \"yyyy-MM-dd'T'HH:mm:ss.SSSSSS\"}")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class DateTimeFormatConfiguration implements Configuration {

    /**
     * The DateTimeFormatter to be used for parsing a date-time String
     */
    @Schema(description = "Format of the date time.", example = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    @NotBlank(message = "The format must not be empty!")
    String dateTimeFormatter;
}
