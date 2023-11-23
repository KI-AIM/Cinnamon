package de.kiaim.platform.model.data.configuration;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class DateTimeFormatConfiguration implements Configuration{

    /**
     * The DateTimeFormatter to be used for parsing a date-time String
     */
    String dateTimeFormatter;
}
