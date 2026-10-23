package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.admin.AppSettingsEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repository for app settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface AppSettingsRepository extends CrudRepository<AppSettingsEntity, Long> {

	/**
	 * Returns the first AppSettingsEntity.
	 * In a healthy state, there is always exactly one entity in the database.
	 *
	 * @return The app settings.
	 */
	Optional<AppSettingsEntity> findFirstBy();

}
