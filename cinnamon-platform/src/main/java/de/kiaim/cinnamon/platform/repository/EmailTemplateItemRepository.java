package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateItemEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for managing email template items in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface EmailTemplateItemRepository extends CrudRepository<EmailTemplateItemEntity, Long> {
}
