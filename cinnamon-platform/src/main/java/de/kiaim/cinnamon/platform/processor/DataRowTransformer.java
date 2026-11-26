package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.exception.DataBuildingException;
import de.kiaim.cinnamon.model.helper.DataTransformationHelper;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.DataTransformationError;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms rows of raw values into {@link DataRow}s, validating each value against the column configuration.
 * Shared by the file processors and the processing of results from external servers.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class DataRowTransformer {

	private final DataTransformationHelper dataTransformationHelper;

	public DataRowTransformer(final DataTransformationHelper dataTransformationHelper) {
		this.dataTransformationHelper = dataTransformationHelper;
	}

	/**
	 * Transforms a row into a DataRow and appends it to the given list of data rows.
	 * Upon transformation, each value is validated.
	 * If a fault has been detected,
	 * the error will be added to the DataRowTransformationError list with the
	 * corresponding raw row string and the row index.
	 * Faulty values are replaced by null values, so the row is still added to the list of DataRows.
	 *
	 * @param row           Input: Row to transform
	 * @param rowIndex      Input index of the row
	 * @param configuration Configuration of the data.
	 * @param dataRows      List containing valid rows
	 * @param errors        List of errors
	 * @throws BadDatasetException If the row has too few or too many values.
	 */
	public void transformRow(final List<@Nullable String> row, final int rowIndex,
	                         final DataConfiguration configuration, final List<DataRow> dataRows,
	                         final List<DataRowTransformationError> errors) throws BadDatasetException {
		if (row.size() < configuration.getConfigurations().size()) {
			throw new BadDatasetException(BadDatasetException.ROW_TOO_FEW_VALUES,
			                              "The row " + (rowIndex + 1) + " contains too few values: expected " +
			                              configuration.getConfigurations().size() + ", but got " + row.size() + "!");
		}
		if (row.size() > configuration.getConfigurations().size()) {
			throw new BadDatasetException(BadDatasetException.ROW_TOO_MANY_VALUES,
			                              "The row " + (rowIndex + 1) + " contains too many values: expected " +
			                              configuration.getConfigurations().size() + ", but got " + row.size() + "!");
		}

		// Process every row
		boolean errorInRow = false;

		List<Data> transformedCol = new ArrayList<>();

		// Transformation error that was thrown inside the Data builders
		DataRowTransformationError newError = new DataRowTransformationError(rowIndex);

		for (int colIndex = 0; colIndex < row.size(); colIndex++) {
			// Process every column value in a row
			String col = row.get(colIndex);

			List<ColumnConfiguration> matchingColumnConfigurations =
					getColumnConfigurationForIndex(configuration.getConfigurations(), colIndex);

			// Every index should appear exactly once
			assert !matchingColumnConfigurations.isEmpty();
			ColumnConfiguration columnConfiguration = matchingColumnConfigurations.get(0);

			try {
				Data transformedData = this.dataTransformationHelper.transformData(col, columnConfiguration);
				transformedCol.add(transformedData);
			} catch (DataBuildingException e) {
				//Resolve to error type, to easier parse information to frontend
				newError.addError(new DataTransformationError(colIndex, e.getTransformationErrorType(), col));

				//Set Flag so error will be added
				errorInRow = true;

				// Remove faulty value from dataset
				transformedCol.add(this.dataTransformationHelper.transformNullValue(columnConfiguration));
			}
		}

		dataRows.add(new DataRow(transformedCol));

		if (errorInRow) {
			//Add error to errorList
			errors.add(newError);
		}
	}

	private List<ColumnConfiguration> getColumnConfigurationForIndex(
			List<ColumnConfiguration> configurations,
			int index
	) {
		return configurations
				.stream()
				.filter(it -> it.getIndex() == index)
				.toList();
	}
}
