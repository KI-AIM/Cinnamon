package de.kiaim.anon.model;

import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import de.kiaim.model.data.DataSet;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AnonymizationRequest {
    private DataSet dataSet;
    private DatasetAnonymizationConfig datasetAnonymizationConfig;
}
