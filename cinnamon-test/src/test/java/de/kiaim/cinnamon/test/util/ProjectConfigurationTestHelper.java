package de.kiaim.cinnamon.test.util;

import de.kiaim.cinnamon.model.configuration.project.MetricConfiguration;
import de.kiaim.cinnamon.model.configuration.project.MetricImportance;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;

/**
 * Helper class for generating project configurations.
 *
 * @author Daniel Preciado-Marquez
 */
public class ProjectConfigurationTestHelper {

	public static ProjectConfigurationDTO generateProjectConfigurationDTO() {
		var dto = new ProjectConfigurationDTO();
		dto.setProjectName("testProject");
		var metricConfiguration = generateMetricConfiguration();
		dto.setMetricConfiguration(metricConfiguration);
		return dto;
	}

	public static MetricConfiguration generateMetricConfiguration() {
		var config = new MetricConfiguration();
		config.setColorScheme("Fluffy Unicorn");
		config.setUseUserDefinedImportance(true);
		config.getUserDefinedImportance().put("MetricA", MetricImportance.IMPORTANT);
		config.getUserDefinedImportance().put("MetricB", MetricImportance.ADDITIONAL);
		config.getUserDefinedImportance().put("MetricC", MetricImportance.NOT_RELEVANT);
		return config;
	}

	public static String generateProjectConfigurationAsYaml() {
		return """
		       project:
		           projectName: "testProject"
		           contactMail: "contact@example.com"
		           metricConfiguration:
		               colorScheme: "Fluffy Unicorn"
		               useUserDefinedImportance: true
		               userDefinedImportance:
		                   "MetricA": "IMPORTANT"
		                   "MetricB": "ADDITIONAL"
		                   "MetricC": "NOT_RELEVANT"
		       """;
	}

	public static String generateProjectConfigurationAsJson() {
		return """
		       {"projectName":"Test Project","contactMail":null,"contactUrl":null,"reportCreator":null,"metricConfiguration":null}""";
	}

	public static String generateProjectConfigurationAsExport() {
		return """
		       project:
		         projectName: "testProject"
		         contactMail: null
		         contactUrl: null
		         reportCreator: null
		         metricConfiguration:
		           colorScheme: "Fluffy Unicorn"
		           useUserDefinedImportance: true
		           userDefinedImportance:
		             MetricA: "IMPORTANT"
		             MetricC: "NOT_RELEVANT"
		             MetricB: "ADDITIONAL"
		       """;
	}

}
