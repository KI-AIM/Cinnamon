package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repository for the singleton {@link EmailSettingsEntity} holding the mail settings of the application.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface EmailSettingsRepository extends CrudRepository<EmailSettingsEntity, Long> {

	/**
	 * Returns the mail settings.
	 * Since there is only ever a single row, the oldest one is returned in case more than one should exist.
	 *
	 * @return The mail settings.
	 */
	Optional<EmailSettingsEntity> findFirstByOrderByIdAsc();

}
