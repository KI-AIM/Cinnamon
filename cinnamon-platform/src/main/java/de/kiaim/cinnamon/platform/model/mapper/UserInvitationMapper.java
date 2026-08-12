package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.dto.UserInvitationInfo;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import de.kiaim.cinnamon.platform.repository.EmailTemplateItemRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * Mapper for {@link UserInvitationEntity} and {@link UserInvitationInfo}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {EmailTemplateItemRepository.class})
public interface UserInvitationMapper {

	UserInvitationInfo toInfo(UserInvitationEntity entity);

	@Mapping(target = "lastSentAt", ignore = true) @Mapping(target = "tokenHash", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "revokedAt", ignore = true) @Mapping(target = "invitedBy", ignore = true)
	@Mapping(target = "expiresAt", ignore = true) @Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "acceptedBy", ignore = true) @Mapping(target = "acceptedAt", ignore = true)
	void updateEntity(@MappingTarget UserInvitationEntity entity, UserInvitationRequest request);
}
