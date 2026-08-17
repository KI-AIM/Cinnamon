package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface UserInvitationRepository extends CrudRepository<UserInvitationEntity, Long> {

	Optional<UserInvitationEntity> findByExternalId(UUID externalId);

	Optional<UserInvitationEntity> findByTokenHash(String tokenHash);

	/**
	 * Returns all invitations that have been accepted or revoked before the given timestamp.
	 *
	 * @param cutoff The timestamp to check against.
	 * @return A list of invitations eligible for deletion.
	 */
	@Query("SELECT i FROM UserInvitationEntity i WHERE " +
	       "(i.status = de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus.ACCEPTED AND i.acceptedAt < :cutoff) OR " +
	       "(i.status = de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus.REVOKED AND i.revokedAt < :cutoff)")
	List<UserInvitationEntity> findAllAcceptedOrRevokedBefore(@Param("cutoff") Timestamp cutoff);

}
