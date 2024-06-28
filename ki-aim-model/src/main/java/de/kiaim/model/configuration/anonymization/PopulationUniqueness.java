package de.kiaim.model.configuration.anonymization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PopulationUniqueness extends PrivacyModel {
    /** Parameter for the privacy model */
    private double riskThreshold;

    @JsonCreator
    public PopulationUniqueness(@JsonProperty("name") String name, @JsonProperty("values") double riskThreshold) {
        super(name);
        this.riskThreshold = riskThreshold;
    }
}
