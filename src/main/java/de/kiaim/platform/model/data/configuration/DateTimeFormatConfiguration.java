package de.kiaim.platform.model.data.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * {@inheritDoc}
     */
    @JsonProperty("name")
    @Override
    public String getName() {
        return DateTimeFormatConfiguration.class.getSimpleName();
    }
}
