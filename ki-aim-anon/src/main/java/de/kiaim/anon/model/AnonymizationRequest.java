package de.kiaim.anon.model;

import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import de.kiaim.model.data.DataSet;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AnonymizationRequest {
    private DataSet dataSet;
    private AnonymizationConfig kiaimAnonConfig;
}
