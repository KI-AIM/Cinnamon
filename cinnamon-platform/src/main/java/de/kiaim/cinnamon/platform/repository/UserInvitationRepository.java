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

/**
 * Repository for managing user invitations.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface UserInvitationRepository extends CrudRepository<UserInvitationEntity, Long> {

	/**
	 * Finds a user invitation by its external ID.
	 *
	 * @param externalId The external ID of the user invitation.
	 * @return An Optional containing the UserInvitationEntity if found, or empty if not found.
	 */
	Optional<UserInvitationEntity> findByExternalId(UUID externalId);

	/**
	 * Finds a user invitation by its hashed token.
	 *
	 * @param tokenHash The hash of the invitation token.
	 * @return An Optional containing the UserInvitationEntity if found, or empty if not found.
	 */
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
