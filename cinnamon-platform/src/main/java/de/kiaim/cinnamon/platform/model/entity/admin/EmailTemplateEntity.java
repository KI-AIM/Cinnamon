package de.kiaim.cinnamon.platform.model.entity.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing an email template in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter @Setter
public class EmailTemplateEntity {

	/**
	 * Internal database ID of the email template.
	 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	/**
	 * The display name of the email template.
	 * Unique so that the template can be referenced by its name.
	 */
	@Column(nullable = false, unique = true)
	private String name;

	/**
	 * The mail content of the email template in different languages.
	 */
	@OneToMany(mappedBy = "emailTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
	private final Set<EmailTemplateItemEntity> items = new HashSet<>();

	/**
	 * Adds the given item to this template. Since an item is unique per
	 * language and template, an existing item is updated instead of adding a
	 * second item for the same language.
	 *
	 * @param item The item to add.
	 */
	public void addItem(final EmailTemplateItemEntity item) {
		if (item == null) {
			return;
		}

		final EmailTemplateItemEntity existingItem = items.stream()
				.filter(existing -> existing.getLanguage() == item.getLanguage())
				.findFirst()
				.orElse(null);

		if (existingItem != null) {
			existingItem.setSubject(item.getSubject());
			existingItem.setBody(item.getBody());
			return;
		}

		items.add(item);
		item.setEmailTemplate(this);
	}

	/**
	 * Removes the given item from this template.
	 *
	 * @param item The item to remove.
	 */
	public void removeItem(final EmailTemplateItemEntity item) {
		if (item == null || !items.remove(item)) {
			return;
		}

		if (item.getEmailTemplate() == this) {
			item.setEmailTemplate(null);
		}
	}

}
