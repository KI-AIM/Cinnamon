package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.dto.UserInvitationInfo;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateItemEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus;
import de.kiaim.cinnamon.platform.repository.EmailTemplateItemRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;

/**
 * Mapper for {@link UserInvitationEntity} and {@link UserInvitationInfo}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class UserInvitationMapper {

	@Autowired
	private EmailTemplateItemRepository emailTemplateItemRepository;

	@Mapping(target = "status", source = "entity", qualifiedByName = "mapStatus")
	@Mapping(target = "emailTemplateItemId", source = "emailTemplateItem.id")
	@Mapping(target = "invitedBy", source = "invitedBy.username")
	@Mapping(target = "acceptedBy", source = "acceptedBy.username")
	public abstract UserInvitationInfo toInfo(UserInvitationEntity entity);

	@Mapping(target = "emailTemplateItem", source = "emailTemplateItemId")
	@Mapping(target = "externalId", ignore = true)
	@Mapping(target = "lastSentAt", ignore = true) @Mapping(target = "tokenHash", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "revokedAt", ignore = true) @Mapping(target = "invitedBy", ignore = true)
	@Mapping(target = "expiresAt", ignore = true) @Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "acceptedBy", ignore = true) @Mapping(target = "acceptedAt", ignore = true)
	public abstract void updateEntity(@MappingTarget UserInvitationEntity entity, UserInvitationRequest request);

	protected EmailTemplateItemEntity map(final Long emailTemplateItemId) {
		if (emailTemplateItemId == null) {
			return null;
		}
		return emailTemplateItemRepository.findById(emailTemplateItemId).orElse(null);
	}

	@Named("mapStatus")
	protected UserInvitationStatus mapStatus(final UserInvitationEntity entity) {
		return entity.isExpired() ? UserInvitationStatus.EXPIRED : entity.getStatus();
	}
}
