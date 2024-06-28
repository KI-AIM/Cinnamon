package de.kiaim.model.configuration.anonymization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AverageReidentificationRisk extends PrivacyModel {
    /**
     * Parameter for the privacy model
     */
    private Double averageRisk;

    @JsonCreator
    public AverageReidentificationRisk(@JsonProperty("name") String name, @JsonProperty("values") double averageRisk) {
        super(name);
        this.averageRisk = averageRisk;
    }
}
