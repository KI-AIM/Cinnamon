package de.kiaim.platform.model.data.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DateTimeFormatConfiguration implements Configuration{

    /**
     * The DateTimeFormatter to be used for parsing a date-time String
     */
    String dateTimeFormatter;

}
