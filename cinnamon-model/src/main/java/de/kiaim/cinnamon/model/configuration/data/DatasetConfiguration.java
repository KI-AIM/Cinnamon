package de.kiaim.cinnamon.model.configuration.data;


import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configurations related to the entire dataset.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configurations related to the entire dataset.")
@NoArgsConstructor
@Getter @Setter
public class DatasetConfiguration implements ConfigurationDTO {

	/**
	 * Whether to create a hold-out split of the dataset for evaluation purposes.
	 * If true, a percentage of the dataset will be set aside as a hold-out set,
	 * which will not be used during anonymization and therefore will not be available in the anonymized dataset.
	 */
	@Schema(description = "Whether to create a hold-out split of the dataset for evaluation purposes.")
	@NotNull
	private Boolean createHoldOutSplit = false;

	/**
	 * Proportion of the dataset that will be used for the hold-out split.
	 */
	@Schema(description = "Proportion of the dataset that will be used for the hold-out split.")
	@NotNull
	private Float holdOutSplitPercentage = 0.2f;


	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public String getKey() {
		return ConfigurationFile.DATASET_CONFIGURATION_KEY;
	}

}
