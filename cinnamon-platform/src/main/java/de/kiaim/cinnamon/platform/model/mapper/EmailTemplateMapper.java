package de.kiaim.cinnamon.platform.model.mapper;

import de.kiaim.cinnamon.platform.model.dto.EmailTemplateDTO;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateItemDTO;
import de.kiaim.cinnamon.platform.model.dto.SupportedLanguageDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateEntity;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateItemEntity;
import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for {@link EmailTemplateEntity} and {@link EmailTemplateDTO}.
 *
 * @author Daniel Preciado-Marquez
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EmailTemplateMapper {

	/**
	 * Maps an entity to the DTO.
	 */
	EmailTemplateDTO toDto(EmailTemplateEntity entity);

	/**
	 * Maps the content of a template to the DTO.
	 */
	EmailTemplateItemDTO toDto(EmailTemplateItemEntity entity);

	/**
	 * Maps a supported language to the DTO.
	 */
	default SupportedLanguageDTO toDto(final SupportedLanguage language) {
		return new SupportedLanguageDTO(language.name(), language.getDisplayName());
	}

	/**
	 * Sorts the content of a template so that the order of the languages is the same for every response.
	 * The content is stored in an unordered set.
	 */
	@AfterMapping
	default void sortItems(@MappingTarget final EmailTemplateDTO dto) {
		if (dto.getItems() != null) {
			dto.getItems().sort(Comparator.comparing(EmailTemplateItemDTO::getLanguage));
		}
	}

	/**
	 * Updates the given entity with the values of the DTO.
	 * The content is handled separately by {@link #updateItems(EmailTemplateEntity, EmailTemplateDTO)}.
	 */
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "items", ignore = true)
	void updateEntity(@MappingTarget EmailTemplateEntity entity, EmailTemplateDTO dto);

	/**
	 * Updates the content of the given entity with the content of the DTO.
	 * The content is not mapped directly because the entity manages the relation to its content itself.
	 * The DTO contains the complete content of the template, so languages that are missing in the DTO are removed.
	 */
	@AfterMapping
	default void updateItems(@MappingTarget final EmailTemplateEntity entity, final EmailTemplateDTO dto) {
		if (dto.getItems() == null) {
			return;
		}

		final Set<SupportedLanguage> languages = dto.getItems().stream()
		                                            .map(EmailTemplateItemDTO::getLanguage)
		                                            .collect(Collectors.toSet());

		// Collected first because the items are removed from the iterated collection.
		final List<EmailTemplateItemEntity> removedItems = entity.getItems().stream()
		                                                         .filter(item -> !languages.contains(item.getLanguage()))
		                                                         .toList();
		removedItems.forEach(entity::removeItem);

		// Updates the content of an already configured language instead of adding a second one.
		dto.getItems().forEach(item -> entity.addItem(toEntity(item)));
	}

	/**
	 * Maps the content of a template to a new entity.
	 * The relation to the template is set by {@link EmailTemplateEntity#addItem(EmailTemplateItemEntity)}.
	 */
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "emailTemplate", ignore = true)
	EmailTemplateItemEntity toEntity(EmailTemplateItemDTO dto);

}
