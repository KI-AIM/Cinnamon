package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Getter
@Entity
@NoArgsConstructor
public class DataConfigurationEntity {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	@Setter
	private DataConfiguration dataConfiguration;

	@OneToOne(mappedBy = "dataConfiguration", optional = false, fetch = FetchType.LAZY, orphanRemoval = false,
	          cascade = CascadeType.PERSIST)
	private UserEntity user;
}
