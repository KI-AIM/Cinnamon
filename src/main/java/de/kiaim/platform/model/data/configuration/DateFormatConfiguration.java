package de.kiaim.platform.model.data.configuration;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "Describes the format of a date.",
        example = "{\"name\": \"DateFormatConfiguration\", \"dataFormatter\": \"yyyy-MM-dd\"}")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class DateFormatConfiguration implements Configuration{

    /**
     * The DateFormatter to be used for parsing a Date String
     */
    @Schema(description = "Format of the date.", example = "yyyy-MM-dd")
    String dateFormatter;
}
