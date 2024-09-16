package de.kiaim.anon.model.dataSetTransformation;

import de.kiaim.model.data.DataSet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Encapsulates traceability information for a List of DataSets that has been merged iteratively.
 * This class provides a structured way to record and retrieve information about how the merged DataSet was created,
 * including details about the original DataSets before they were combined.
 */

@AllArgsConstructor
@Getter
@Setter
@ToString
public class MergedDataSetTraceabilityList {

    private final DataSet mergedDataSet;

    private final List<DataSetSplittingInformation> dataSetSplittingInformationList;

}
