package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@link EmailTemplateEntity email templates} of the application.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface EmailTemplateRepository extends CrudRepository<EmailTemplateEntity, Long> {

	/**
	 * Returns all templates sorted by their name.
	 * The content of the templates is fetched together with the templates.
	 *
	 * @return All templates.
	 */
	@EntityGraph(attributePaths = "items")
	List<EmailTemplateEntity> findAllByOrderByNameAsc();

	/**
	 * Returns the template with the given name.
	 *
	 * @param name The name of the template.
	 * @return The template or an empty optional if no template with the given name exists.
	 */
	Optional<EmailTemplateEntity> findByName(String name);

}
