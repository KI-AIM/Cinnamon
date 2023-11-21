package de.kiaim.platform.helper;

import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import org.springframework.stereotype.Service;

@Service
public class DataschemeGenerator {

	public String createSchema(final DataConfiguration dataConfiguration, final String tableName) {
		String query = "CREATE TABLE " + tableName +
		               "(" +
		               "%s" +
		               ")";
		StringBuilder columns = new StringBuilder();

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
					columns.append(createColumnString(columnConfiguration.getName(), "character varying(255)"));
				}
			}
			if (i < dataConfiguration.getConfigurations().size() - 1) {
				columns.append(",");
			}

		}
		return query.formatted(columns.toString());
	}

	private String createColumnString(final String columnName, final String dataType) {
		return columnName + " " + dataType + " NOT NULL";
	}

}
