package de.kiaim.platform.model.data.configuration;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class DateFormatConfiguration implements Configuration{

    /**
     * The DateFormatter to be used for parsing a Date String
     */
    String dateFormatter;
}
