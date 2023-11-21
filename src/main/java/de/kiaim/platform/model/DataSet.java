package de.kiaim.platform.model;

import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DataSet {

	final List<DataRow> dataRows;

	final DataConfiguration dataConfiguration;
}
