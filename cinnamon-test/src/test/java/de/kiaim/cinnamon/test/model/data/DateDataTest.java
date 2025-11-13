package de.kiaim.cinnamon.test.model.data;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.configuration.data.DateFormatConfiguration;
import de.kiaim.cinnamon.model.data.DateData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DateDataTest {

	private DateData.DateDataBuilder builder;

	@BeforeEach
	public void setup() {
		builder = new DateData.DateDataBuilder();
	}

	@Test
	public void estimateColumnConfigurationFhirYear() {
		String value = "2018";
		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.UNDEFINED, estimation.getType());
	}

	@Test
	public void estimateColumnConfigurationFhirYearMonth() {
		String value = "1973-06";
		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.UNDEFINED, estimation.getType());
	}

	@Test
	public void estimateColumnConfigurationFhirYearMonthDay() {
		String value = "1905-08-23";
		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateFormatConfiguration dateFormat = assertInstanceOf(DateFormatConfiguration.class, config);
		assertEquals("yyyy-MM-dd", dateFormat.getDateFormatter());
	}

}
