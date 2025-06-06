package de.kiaim.cinnamon.model.configuration.anonymization.frontend;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class FrontendPrivacyModelConfig {
    private String name;
    private String type;
    private FrontendModelConfig modelConfiguration;

}
