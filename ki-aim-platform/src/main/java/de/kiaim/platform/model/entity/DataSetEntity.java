package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.converter.StepListAttributeConverter;
import de.kiaim.platform.model.enumeration.Step;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entity containing the metadata of a data set.
 * The data is stored in a separate table.
 * The ID is used for identifying the table in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@NoArgsConstructor
@Getter @Entity
public class DataSetEntity extends ProcessOwner {

	/**
	 * The data configuration.
	 */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	@Setter
	private DataConfiguration dataConfiguration;

	/**
	 * If the data has been stored into the extra table.
	 */
	@Column(nullable = false)
	@Setter
	private boolean storedData = false;

	/**
	 * If the data has been stored and confirmed.
	 */
	@Column(nullable = false)
	@Setter
	private boolean confirmedData = false;

	/**
	 * List of transformation errors during the parsing.
	 */
	@OneToMany(mappedBy = "dataSet", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	private final Set<DataTransformationErrorEntity> dataTransformationErrors = new HashSet<>();

	/**
	 * List of steps that have modified this data set.
	 */
	@Convert(converter = StepListAttributeConverter.class)
	@Setter
	private List<Step> processed = new ArrayList<>();

	/**
	 * Process for calculating the statistics.
	 */
	@OneToOne(mappedBy = "owner", optional = false, orphanRemoval = true, cascade = CascadeType.ALL)
	@Getter
	private final BackgroundProcessEntity statisticsProcess = new BackgroundProcessEntity(this);

	/**
	 * The corresponding original data.
	 * Only originalData or job is allowed to be set.
	 */
	@JsonIgnore
	@OneToOne(fetch = FetchType.EAGER, orphanRemoval = false, optional = true)
	@Nullable
	private OriginalDataEntity originalData = null;

	/**
	 * The corresponding job.
	 * Only originalData or job is allowed to be set.
	 */
	@JsonIgnore
	@OneToOne(fetch = FetchType.EAGER, orphanRemoval = false, optional = true)
	@Nullable
	private DataProcessingEntity job = null;

	/**
	 * Creates a new data set for th given original data.
	 * @param originalData The original data entity.
	 */
	public DataSetEntity(final OriginalDataEntity originalData) {
		this.setOriginalData(originalData);
	}

	/**
	 * Creates a new data set for th given process.
	 * @param dataProcessing The process.
	 */
	public DataSetEntity(final DataProcessingEntity dataProcessing) {
		this.setJob(dataProcessing);
	}

	public Long getId() {
		return this.id;
	}

	/**
	 * Adds the given transformation error to the data set.
	 * @param dataTransformationError The error to be added.
	 */
	public void addDataRowTransformationError(final DataTransformationErrorEntity dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);

		if (dataTransformationError.getDataSet() != this) {
			dataTransformationError.setDataSet(this);
		}
	}

	/**
	 * Links the given original data entity with this data set.
	 * @param newDataSet The entity to be linked.
	 */
	public void setOriginalData(@Nullable final OriginalDataEntity newDataSet) {
		final OriginalDataEntity oldOriginalData = this.originalData;
		this.originalData = newDataSet;
		if (oldOriginalData != null && oldOriginalData.getDataSet() == this) {
			oldOriginalData.setDataSet(null);
		}
		if (newDataSet != null && newDataSet.getDataSet() != this) {
			newDataSet.setDataSet(this);
		}
	}

	/**
	 * Links the given process with this data set.
	 * @param newJob The process to be linked.
	 */
	public void setJob(@Nullable final DataProcessingEntity newJob) {
		final DataProcessingEntity oldJob = this.job;
		this.job = newJob;
		if (oldJob != null && oldJob.getDataSet() == this) {
			oldJob.setDataSet(null);
		}
		if (newJob != null && newJob.getDataSet() != this) {
			newJob.setDataSet(this);
		}
	}

	public Step getStep() {
		if (this.originalData != null) {
			return Step.VALIDATION;
		} else {
			return this.job.getStep();
		}
	}

	/**
	 * Validates that exactly one filed of original data or process is set.
	 */
	@PrePersist
	@PreUpdate
	private void validateRelation() {
		if (this.originalData != null && this.job != null) {
			throw new IllegalStateException("Only one of originalData and job should be set");
		}
		if (this.originalData == null && this.job == null) {
			throw new IllegalStateException("One of originalData and job should be set");
		}
	}

	@Override
	public ProjectEntity getProject() {
		if (this.originalData != null) {
			return this.originalData.getProject();
		} else if (this.job != null) {
			return this.job.getProject();
		}
		return null;
	}

	/**
	 * Returns the statistics of the data set.
	 * @return The statistics.
	 */
	public LobWrapperEntity getStatistics() {
		// TODO move name in application.properties
		return this.statisticsProcess.getResultFiles().get("metrics.yml");
	}
}
