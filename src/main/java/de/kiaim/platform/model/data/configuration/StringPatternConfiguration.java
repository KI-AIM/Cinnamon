package de.kiaim.platform.model.data.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StringPatternConfiguration implements Configuration {

    /**
     * The Regex that a string should match
     */
    String pattern;

}
