package de.kiaim.anon.model;

import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.data.DataSet;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AnonymizationRequest {
    private final String session_key;
    private final DataSet data;
    private final FrontendAnonConfig anonymizationConfig;
    private final String callback;
}
