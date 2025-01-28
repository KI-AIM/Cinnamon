package de.kiaim.platform.helper;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.exception.BadDataConfigurationException;
import org.springframework.stereotype.Service;

@Service
public class DataschemeGenerator {

	public static final String HOLT_OUT_FLAG_NAME = "is_hold_out";

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
		String query = "CREATE TABLE " + tableName +
		               "(" +
		               "%s" +
		               ");";
		StringBuilder columns = new StringBuilder();

		// Add columns from the data set
		for (int i = 0; i < dataConfiguration.getConfigurations().size(); ++i) {
			final ColumnConfiguration columnConfiguration = dataConfiguration.getConfigurations().get(i);
			switch (columnConfiguration.getType()) {
				case BOOLEAN -> {
					columns.append(createColumnString(columnConfiguration.getName(), "boolean"));
				}
				case DATE -> {
					columns.append(createColumnString(columnConfiguration.getName(), "date"));
				}
				case DATE_TIME -> {
					columns.append(createColumnString(columnConfiguration.getName(), "timestamp"));
				}
				case DECIMAL -> {
					columns.append(createColumnString(columnConfiguration.getName(), "numeric"));
				}
				case INTEGER -> {
					columns.append(createColumnString(columnConfiguration.getName(), "integer"));
				}
				case STRING -> {
					columns.append(createColumnString(columnConfiguration.getName(), "character varying"));
				}
				case UNDEFINED -> {
					throw new BadDataConfigurationException(BadDataConfigurationException.UNDEFINED_DATA_TYPE,
					                                        "Can't create table schema with an undefined column type!");
				}
			}
			columns.append(",");
		}

		// Add column for hold out flag
		columns.append(createColumnString(HOLT_OUT_FLAG_NAME, "boolean"));

		return query.formatted(columns.toString());
	}

	private String createColumnString(final String columnName, final String dataType) {
		return "\"" + columnName + "\" " + dataType;
	}

}
