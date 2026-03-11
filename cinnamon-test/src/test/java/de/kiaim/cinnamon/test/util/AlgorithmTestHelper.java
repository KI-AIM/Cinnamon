package de.kiaim.cinnamon.test.util;

import de.kiaim.cinnamon.model.configuration.algorithms.Algorithm;
import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmDefinition;
import de.kiaim.cinnamon.model.configuration.algorithms.AvailableAlgorithms;

import java.util.List;

/**
 * Various helper methods for testing algorithms.
 *
 * @author Daniel Preciado-Marquez
 */
public class AlgorithmTestHelper {

	public static AvailableAlgorithms generateAvailableAlgorithms() {
		var expected = new AvailableAlgorithms();
		expected.setAlgorithms(List.of(
				new Algorithm("algorithmA", "Algorithm A", "This is algorithm A", "tabular", "1.0.1", "/algorithmA")));
		return expected;
	}

	public static String generateAvailableAlgorithmsYaml() {
		return """
		       algorithms:
		       - name: algorithmA
		         display_name: Algorithm A
		         description: This is algorithm A
		         type: tabular
		         version: 1.0.1
		         URL: /algorithmA
		       """;
	}

	public static AlgorithmDefinition generateAlgorithmDefinition() {
		return new AlgorithmDefinition("AlgorithmA", "1.0.1", "tabular", "/algorithmA");
	}

	public static String generateAlgorithmDefinitionYaml() {
		return """
		       name: algorithmA
		       display_name: Algorithm A
		       description: This is algorithm A
		       type: tabular
		       version: 1.0.1
		       URL: /algorithmA
		       modelConfiguration:
		           display_name: Privacy Model Configuration
		           description: Define the risk threshold, generalization setting and suppression limit which will be used to run the anonymization.
		           parameters:
		             - name: riskThresholdType
		               label: Risk Threshold Type
		               type: string
		               description: Select the risk threshold type, either 'Max' for e.g. k-Anonymity enforcement or 'Avg' for an average risk threshold.
		               default_value: 'Max'
		               values: ['Max', 'Avg']
		             - name: riskThresholdValue
		               label: Risk Threshold Value
		               type: float
		               description: Select the threshold value based on the risk type. # AP : should we provide an example ?
		               default_value: 0.1
		               min_value: 0.0
		               max_value: $dataset.original.numberHoldOutRows
		       """;
	}

	public static String generateAlgorithmConfigurationYaml() {
		return """
		       anonymization:
		          privacyModels:
		          - name: algorithmA
		       """;
	}

	public static String generateAlgorithmConfigurationJson() {
		return """
		       {"anonymization":{"privacyModels":[{"name":"algorithmA"}]}}""";
	}

	public static AvailableAlgorithms generateAvailableAlgorithms2() {
		return new AvailableAlgorithms(List.of(
				new Algorithm("ctgan", "", "", "", "", "/algorithm/ctgan")
		));
	}

	public static AlgorithmDefinition generateAlgorithmDefinition2() {
		return new AlgorithmDefinition("ctgan", "1.0.1", "tabular", "/start_synthetization_process/ctgan");
	}

	public static String generateAlgorithmConfiguration2() {
		return """
		       synthetization_configuration:
		          algorithm:
		              synthesizer: ctgan
		       """;
	}
}
