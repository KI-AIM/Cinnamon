package de.kiaim.model.configuration.anonymization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class KAnonymity extends PrivacyModel {

    /**
     * Parameter for the privacy model
     */
    private int k;

    @JsonCreator
    public KAnonymity(@JsonProperty("name") String name, @JsonProperty("values") int k) {
        super(name);
        this.k = k;
    }
}