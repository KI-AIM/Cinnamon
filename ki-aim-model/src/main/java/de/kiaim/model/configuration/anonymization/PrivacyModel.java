package de.kiaim.model.configuration.anonymization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Schema(description = "Privacy model for AnonymizationConfiguration.")
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KAnonymity.class, name = "K-Anon"),
        @JsonSubTypes.Type(value = AverageReidentificationRisk.class, name = "AvgRisk"),
        @JsonSubTypes.Type(value = PopulationUniqueness.class, name = "Uniqueness")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivacyModel {
    private final String name;
}
