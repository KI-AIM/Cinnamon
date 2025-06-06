package de.kiaim.cinnamon.anonymization.model.dataSetTransformation;

import de.kiaim.cinnamon.model.data.DataSet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Encapsulates traceability information for a DataSet that has been merged from two other DataSets.
 * This class provides a structured way to record and retrieve information about how the merged DataSet was created,
 * including details about the original DataSets before they were combined.
 *
 * Attributes:
 * - mergedDataSet: The resulting DataSet after merging two source DataSets.
 * - leftDataSetSplittingInformation: Details on how the left (or first) DataSet was prepared or manipulated before merging.
 * - rightDataSetSplittingInformation: Details on how the right (or second) DataSet was prepared or manipulated before merging.
 */
@AllArgsConstructor
@Getter
@Setter
@ToString
public class MergedDataSetTraceability {

    private final DataSet mergedDataSet;

    private final DataSetSplittingInformation leftDataSetSplittingInformation;

    private final DataSetSplittingInformation rightDataSetSplittingInformation;


}
