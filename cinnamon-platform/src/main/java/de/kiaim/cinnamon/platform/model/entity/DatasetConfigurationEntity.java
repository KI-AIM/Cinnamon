package de.kiaim.cinnamon.platform.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity class for the dataset configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Embeddable
@Getter @Setter
public class DatasetConfigurationEntity {

	/*
	 * Whether to create a hold-out split of the dataset for evaluation purposes.
	 * If true, a percentage of the dataset will be set aside as a hold-out split,
	 * which will not be used during anonymization and therefore will not be available in the anonymized dataset.
	 */
	@Column(nullable = false)
	private boolean createHoldOutSplit = false;

	/**
	 * Proportion of the dataset that will be used for the hold-out split.
	 */
	@Column(nullable = false)
	private float holdOutSplitPercentage = 0.0f;
}
