package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface UserInvitationRepository extends CrudRepository<UserInvitationEntity, Long> {

	Optional<UserInvitationEntity> findByExternalId(UUID externalId);

	Optional<UserInvitationEntity> findByTokenHash(String tokenHash);

}
