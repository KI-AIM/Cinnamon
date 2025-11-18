package de.kiaim.cinnamon.anonymization.service;

import de.kiaim.cinnamon.anonymization.exception.ReportException;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAnonConfigWrapper;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAttributeConfig;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendModelConfig;
import de.kiaim.cinnamon.model.dto.ModuleReportContent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for generating the report content.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ReportService {

	/**
	 * Generates the content for the report.
	 *
	 * @param configuration The anonymization configuration.
	 * @return The report content.
	 */
	public ModuleReportContent getReportContent(final FrontendAnonConfigWrapper configuration) {
		final String configDescription = getConfigDescription(configuration);
		final String glossary = loadReportGlossary();

		return new ModuleReportContent(configDescription, glossary);
	}

	/**
	 * Generates a textual description of the given anonymization configuration.
	 *
	 * @param configuration The configuration.
	 * @return The description.
	 */
	private String getConfigDescription(final FrontendAnonConfigWrapper configuration) {
		final FrontendModelConfig model = configuration.getAnonymization().getPrivacyModels().get(0).getModelConfiguration();
		final String riskThresholdType = Objects.equals(model.getRiskThresholdType(), "Max")
		                                 ? "maximum"
		                                 : "average";
		final String riskThresholdValue = (model.getRiskThresholdValue() * 100) + "%";

		final String numberOfAttributes = writeNumber(configuration.getAnonymization().getAttributeConfiguration().size());
		final String attributes = applyNumerus(configuration.getAnonymization().getAttributeConfiguration().size(), "attribute", "attributes");
		final String namesOfAttributes = configuration.getAnonymization().getAttributeConfiguration()
		                                              .stream()
		                                              .map(FrontendAttributeConfig::getName)
		                                              .collect(Collectors.joining(", "));
		final String generalization = model.getGeneralizationSetting().toLowerCase();

		final StringBuilder protection = new StringBuilder();
		for (final var p : summarizeAttributeAnonymization(configuration.getAnonymization().getAttributeConfiguration()).entrySet()) {
			protection.append("<p>The");
			protection.append(applyNumerus(p.getValue().size(), " one attribute, that was ", " " + writeNumber(p.getValue().size()) + " attributes, that were "));
			protection.append("protected via <strong>").append(p.getKey()).append("</strong>, ");
			protection.append(applyNumerus(p.getValue().size(), "was ", "were "));
			protection.append("<strong>");

			if (p.getValue().size() == 1) {
				protection.append(p.getValue().get(0));
			} else {
				protection.append(String.join(", ", p.getValue().subList(0, p.getValue().size() - 1)));
				protection.append(" and ").append(p.getValue().get(p.getValue().size() - 1));
			}

			protection.append("</strong>.</p>");
		}

		return """
		       <p>
		       The used privacy model optimized the dataset to reach a <strong>%s residual risk of %s</strong> based <strong>on %s %s (%s)</strong>.
		       This means that all records are indistinguishable to one other record in these attributes.
		       The generalization was set to be <strong>%s</strong>, meaning that all values in a column have the same underlying generalization interval.
		       </p>
		       %s
		       <p>
		       No other protection mechanism was applied.
		       </p>
		       <p>
		       For further details, look into the dedicated anonymization section of this report.
		       </p>
		       """.formatted(riskThresholdType, riskThresholdValue, numberOfAttributes, attributes, namesOfAttributes,
		                     generalization, protection.toString());
	}

	/**
	 * Summerizes, which protection methods have been applied.
	 * Returns a map, where the keys are the display names of the protection types
	 * and the values are a list of the attributes the protection was applied to.
	 *
	 * @param configs The attribute configurations.
	 * @return The map.
	 */
	private Map<String, List<String>> summarizeAttributeAnonymization(final List<FrontendAttributeConfig> configs) {
		final Map<String, List<String>> methods = new ConcurrentHashMap<>();

		for (final var attribute : configs) {
			final var name = attribute.getName();
			var protection = attribute.getAttributeProtection().getDisplayName();

			if (methods.containsKey(protection)) {
				methods.get(protection).add(name);
			} else {
				methods.put(protection, new ArrayList<>(List.of(name)));
			}
		}

		return methods;
	}

	/**
	 * Converts the given number into a string.
	 * Values between 1 and 9 are written out.
	 *
	 * @param number The number.
	 * @return The textual representation of the number.
	 */
	private String writeNumber(final int number) {
		return switch (number) {
			case 1 -> "one";
			case 2 -> "two";
			case 3 -> "three";
			case 4 -> "four";
			case 5 -> "five";
			case 6 -> "six";
			case 7 -> "seven";
			case 8 -> "eight";
			case 9 -> "nine";
			default -> number + "";
		};
	}

	/**
	 * Applies the correct numerus.
	 *
	 * @param number   The number.
	 * @param singular The singular form of the word.
	 * @param plural   The plural form of the word.
	 * @return The transformed string.
	 */
	private String applyNumerus(final int number, final String singular, final String plural) {
		return number == 1 ? singular : plural;
	}

	/**
	 * Loads the glossary from the classpath.
	 *
	 * @return The glossary's HTML content as a string.
	 */
	private String loadReportGlossary() {
		final Resource resource = new ClassPathResource("report/glossary.html");
		try {
			return resource.getContentAsString(Charset.defaultCharset());
		} catch (final IOException e) {
			throw new ReportException("Error loading the report glossary.", e);
		}
	}

}
