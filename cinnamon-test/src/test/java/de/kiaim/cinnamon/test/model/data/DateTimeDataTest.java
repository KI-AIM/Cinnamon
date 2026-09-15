package de.kiaim.cinnamon.test.model.data;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	public void estimateColumnConfiguration24HourClock() {
		String value = "2024-01-01 14:30:00";

		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE_TIME, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateTimeFormatConfiguration dateTimeFormat = assertInstanceOf(DateTimeFormatConfiguration.class, config);
		assertEquals("yyyy-MM-dd HH:mm:ss", dateTimeFormat.getDateTimeFormatter());
	}

	@Test
	public void estimateColumnConfigurationMillisAndOffset() {
		String value = "2024-01-01T14:30:00.123+02:00";

		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE_TIME, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateTimeFormatConfiguration dateTimeFormat = assertInstanceOf(DateTimeFormatConfiguration.class, config);
		assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", dateTimeFormat.getDateTimeFormatter());
	}

	@Test
	public void estimateFormatForSamplesResolvesDayFirstUsingColumnEvidence() {
		// "13-04-2020 10:00:00" can only be day-first, which should disambiguate the whole column
		// in favor of dd-MM-yyyy even though "05-04-2020 10:00:00" alone is ambiguous.
		List<String> samples = List.of("13-04-2020 10:00:00", "05-04-2020 10:00:00");

		Optional<String> format = builder.estimateFormatForSamples(samples);

		assertTrue(format.isPresent());
		assertEquals("dd-MM-yyyy HH:mm:ss", format.get());
	}

	@Test
	public void setValue() {
		var value = "2017-01-01T08:15:00";
		List<Configuration> config = List.of(new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ss"));

		assertDoesNotThrow(() -> builder.setValue(value, config));

		assertEquals(LocalDateTime.of(2017, 1, 1, 8, 15), builder.build().getValue());
	}

	@Test
	public void setValueWithTimezoneX() {
		var value = "2017-01-01T08:15:00+02";
		List<Configuration> config = List.of(new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ssX"));

		assertDoesNotThrow(() -> builder.setValue(value, config));

		assertEquals(LocalDateTime.of(2017, 1, 1, 6, 15), builder.build().getValue());
	}

	@Test
	public void setValueWithTimezoneXX() {
		var value = "2017-01-01T08:15:00+0200";
		List<Configuration> config = List.of(new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ssXX"));

		assertDoesNotThrow(() -> builder.setValue(value, config));

		assertEquals(LocalDateTime.of(2017, 1, 1, 6, 15), builder.build().getValue());
	}

	@Test
	public void setValueWithTimezoneXXX() {
		var value = "2017-01-01T08:15:00+02:00";
		List<Configuration> config = List.of(new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ssXXX"));

		assertDoesNotThrow(() -> builder.setValue(value, config));

		assertEquals(LocalDateTime.of(2017, 1, 1, 6, 15), builder.build().getValue());
	}

	@Test
	public void setValueWithTimezoneZ() {
		var value = "2017-01-01T08:15:00Z";
		List<Configuration> config = List.of(new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ss'Z'"));

		assertDoesNotThrow(() -> builder.setValue(value, config));

		assertEquals(LocalDateTime.of(2017, 1, 1, 8, 15), builder.build().getValue());
	}

	@Test
	public void setValueWithTimezoneVV() {
		var value = "2017-01-01T08:15:00 Africa/Johannesburg";
		List<Configuration> config = List.of(new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ss VV"));

		assertDoesNotThrow(() -> builder.setValue(value, config));

		assertEquals(LocalDateTime.of(2017, 1, 1, 6, 15), builder.build().getValue());
	}

}
