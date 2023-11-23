package de.kiaim.platform.model.data.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DateFormatConfiguration implements Configuration{

    /**
     * The DateFormatter to be used for parsing a Date String
     */
    String dateFormatter;
}
