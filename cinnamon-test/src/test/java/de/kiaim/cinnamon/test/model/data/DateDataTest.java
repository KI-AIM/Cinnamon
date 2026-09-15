package de.kiaim.cinnamon.test.model.data;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.data.DateData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	public void estimateFormatForSamplesMonthNameVariant() {
		String value = "Mar 4, 2020";
		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateFormatConfiguration dateFormat = assertInstanceOf(DateFormatConfiguration.class, config);
		assertEquals("MMM d, yyyy", dateFormat.getDateFormatter());
	}

	@Test
	public void estimateFormatForSamplesCompactIso() {
		String value = "20200401";
		ColumnConfiguration estimation = builder.estimateColumnConfiguration(value);

		assertEquals(DataType.DATE, estimation.getType());

		Configuration config = estimation.getConfigurations().get(0);
		DateFormatConfiguration dateFormat = assertInstanceOf(DateFormatConfiguration.class, config);
		assertEquals("yyyyMMdd", dateFormat.getDateFormatter());
	}

	@Test
	public void estimateFormatForSamplesResolvesDayFirstUsingColumnEvidence() {
		// "13/04/2020" can only be day-first (13 is not a valid month), which should disambiguate
		// the whole column in favor of dd/MM/yyyy even though "05/04/2020" alone is ambiguous.
		List<String> samples = List.of("13/04/2020", "05/04/2020");

		Optional<String> format = builder.estimateFormatForSamples(samples);

		assertTrue(format.isPresent());
		assertEquals("dd/MM/yyyy", format.get());
	}

	@Test
	public void estimateFormatForSamplesResolvesMonthFirstUsingColumnEvidence() {
		// "04/13/2020" can only be month-first (13 is not a valid day for dd/MM), which should disambiguate
		// the whole column in favor of MM/dd/yyyy even though "04/05/2020" alone is ambiguous.
		List<String> samples = List.of("04/13/2020", "04/05/2020");

		Optional<String> format = builder.estimateFormatForSamples(samples);

		assertTrue(format.isPresent());
		assertEquals("MM/dd/yyyy", format.get());
	}

	@Test
	public void estimateFormatForSamplesReturnsEmptyWhenNoFormatMatches() {
		List<String> samples = List.of("not a date");

		Optional<String> format = builder.estimateFormatForSamples(samples);

		assertEquals(Optional.empty(), format);
	}

}
