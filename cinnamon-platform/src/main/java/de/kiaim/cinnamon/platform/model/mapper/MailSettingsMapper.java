package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/**
 * Mapper for {@link EmailSettingsEntity} and {@link EMailSettingsDTO}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MailSettingsMapper {

	/**
	 * Maps an entity to the DTO.
	 * The password itself is never part of the DTO, only whether one has been configured.
	 */
	@Mapping(target = "mailPassword", ignore = true)
	@Mapping(target = "mailPasswordSet", source = "mailPassword", qualifiedByName = "isPasswordSet")
	EMailSettingsDTO toDto(EmailSettingsEntity entity);

	/**
	 * Updates the given entity with the values of the DTO.
	 */
	@Mapping(target = "id", ignore = true)
	void updateEntity(@MappingTarget EmailSettingsEntity entity, EMailSettingsDTO dto);

	@Named("isPasswordSet")
	default boolean isPasswordSet(final String password) {
		return password != null && !password.isBlank();
	}

}
