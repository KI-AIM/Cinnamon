package de.kiaim.platform.model.mapper;

import de.kiaim.platform.model.dto.ProjectConfigurationDTO;
import de.kiaim.platform.model.entity.ProjectConfigurationEntity;
import org.mapstruct.*;

/**
 * Mapper for {@link ProjectConfigurationEntity} and {@link ProjectConfigurationDTO}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectConfigurationMapper {

	/**
	 * Maps an entity to the DTO.
	 */
	ProjectConfigurationDTO toDto(ProjectConfigurationEntity entity);

	/**
	 * Updates the given entity.
	 */
	@Mapping(target = "project", ignore = true)
	void updateEntity(@MappingTarget ProjectConfigurationEntity entity, ProjectConfigurationDTO dto);
}
