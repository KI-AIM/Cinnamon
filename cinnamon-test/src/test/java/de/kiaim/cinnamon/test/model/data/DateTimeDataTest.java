package de.kiaim.cinnamon.test.model.data;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.configuration.data.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DateTimeDataTest {

	private DateTimeData.DateTimeDataBuilder builder;

	@BeforeEach
	public void setup() {
		builder = new DateTimeData.DateTimeDataBuilder();
	}

	@Test
	public void estimateColumnConfigurationFhir() {
		String value = "2015-02-07T13:28:17-05:00";
		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE_TIME, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateTimeFormatConfiguration dateFormat = assertInstanceOf(DateTimeFormatConfiguration.class, config);
		assertEquals("yyyy-MM-dd'T'HH:mm:ssXXX", dateFormat.getDateTimeFormatter());
	}

	@Test
	public void estimateColumnConfigurationFhirZ() {
		String value = "2017-01-01T00:00:00.000Z";

		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE_TIME, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateTimeFormatConfiguration dateFormat = assertInstanceOf(DateTimeFormatConfiguration.class, config);
		assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", dateFormat.getDateTimeFormatter());
	}

}
