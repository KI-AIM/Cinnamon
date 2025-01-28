package de.kiaim.platform.helper;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.exception.BadDataConfigurationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataschemeGenerator {

	public static final String HOLT_OUT_FLAG_NAME = "is_hold_out";
	public static final String ROW_INDEX_NAME = "row_index";

	/**
	 * Creates a statement to create a table with the given name in the database based on the given DataConfiguration.
	 *
	 * @param dataConfiguration DataConfiguration for creating the schema.
	 * @param tableName         Name of the table.
	 * @return Query in the form of a String.
	 * @throws BadDataConfigurationException If an attributes has an undefined data type.
	 */
	public String createSchema(final DataConfiguration dataConfiguration, final String tableName)
			throws BadDataConfigurationException {
		final String query = "CREATE TABLE " + tableName +
		                     "(" +
		                     "%s" +
		                     ");";

		final List<String> columns = new ArrayList<>();

		// Add columns from the data set
		for (int i = 0; i < dataConfiguration.getConfigurations().size(); ++i) {
			final ColumnConfiguration columnConfiguration = dataConfiguration.getConfigurations().get(i);
			switch (columnConfiguration.getType()) {
				case BOOLEAN -> {
					columns.add(createColumnString(columnConfiguration.getName(), "boolean"));
				}
				case DATE -> {
					columns.add(createColumnString(columnConfiguration.getName(), "date"));
				}
				case DATE_TIME -> {
					columns.add(createColumnString(columnConfiguration.getName(), "timestamp"));
				}
				case DECIMAL -> {
					columns.add(createColumnString(columnConfiguration.getName(), "numeric"));
				}
				case INTEGER -> {
					columns.add(createColumnString(columnConfiguration.getName(), "integer"));
				}
				case STRING -> {
					columns.add(createColumnString(columnConfiguration.getName(), "character varying"));
				}
				case UNDEFINED -> {
					throw new BadDataConfigurationException(BadDataConfigurationException.UNDEFINED_DATA_TYPE,
					                                        "Can't create table schema with an undefined column type!");
				}
			}
		}

		// Add column for hold out flag
		columns.add(createColumnString(HOLT_OUT_FLAG_NAME, "boolean"));

		// Add column for row number
		columns.add(createColumnString(ROW_INDEX_NAME, "integer"));

		return query.formatted(String.join(",", columns));
	}

	private String createColumnString(final String columnName, final String dataType) {
		return "\"" + columnName + "\" " + dataType;
	}

}
