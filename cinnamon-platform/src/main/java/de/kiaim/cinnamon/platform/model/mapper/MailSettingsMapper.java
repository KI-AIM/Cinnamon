package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import org.mapstruct.AfterMapping;
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
	 * The password is handled separately by {@link #updatePassword(EmailSettingsEntity, EMailSettingsDTO)}.
	 */
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "mailPassword", ignore = true)
	void updateEntity(@MappingTarget EmailSettingsEntity entity, EMailSettingsDTO dto);

	/**
	 * Sets the password of the entity if the DTO contains a new one.
	 * Since the password is never part of a response, a missing password means that the stored one should be kept.
	 */
	@AfterMapping
	default void updatePassword(@MappingTarget final EmailSettingsEntity entity, final EMailSettingsDTO dto) {
		if (dto.getMailPassword() != null && !dto.getMailPassword().isBlank()) {
			entity.setMailPassword(dto.getMailPassword());
		}
	}

	@Named("isPasswordSet")
	default boolean isPasswordSet(final String password) {
		return password != null && !password.isBlank();
	}

}
