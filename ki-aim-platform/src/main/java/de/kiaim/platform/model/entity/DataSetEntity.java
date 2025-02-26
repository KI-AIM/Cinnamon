package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.model.enumeration.Step;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.HashSet;
import java.util.Set;

@Getter @Entity
public class DataSetEntity {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Associated step of the data set.
	 */
	@JsonIgnore
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	@Setter
	private Step step;

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
	 * The corresponding project.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@Setter
	private ProjectEntity project;

	public void addDataRowTransformationError(final DataTransformationErrorEntity dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);

		if (dataTransformationError.getDataSet() != this) {
			dataTransformationError.setDataSet(this);
		}
	}
}
