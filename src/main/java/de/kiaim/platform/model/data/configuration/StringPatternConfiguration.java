package de.kiaim.platform.model.data.configuration;

import lombok.*;

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
    String pattern;
}
