package de.kiaim.cinnamon.anonymization.model.dataSetTransformation;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Information to retrieve the original dataset after merging.
 */
@AllArgsConstructor
@Getter
@Setter
public class DataSetSplittingInformation {

    private final DataConfiguration dataConfiguration;

    private final Set<Data> indexes;
}
