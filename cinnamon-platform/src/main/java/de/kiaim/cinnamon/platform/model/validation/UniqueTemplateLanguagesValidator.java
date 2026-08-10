package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.dto.EmailTemplateDTO;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateItemDTO;
import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Validator for {@link UniqueTemplateLanguages}.
 * Since a template stores at most one content per language, a request containing a language twice is ambiguous.
 *
 * @author Daniel Preciado-Marquez
 */
public class UniqueTemplateLanguagesValidator implements ConstraintValidator<UniqueTemplateLanguages, EmailTemplateDTO> {

	@Override
	public boolean isValid(final EmailTemplateDTO template, final ConstraintValidatorContext context) {
		if (template.getItems() == null) {
			return true;
		}

		final Set<SupportedLanguage> languages = new HashSet<>();
		for (final EmailTemplateItemDTO item : template.getItems()) {
			// Missing languages are reported by the constraints of the item itself.
			if (item == null || item.getLanguage() == null) {
				continue;
			}

			if (!languages.add(item.getLanguage())) {
				// Report the violation for the items instead of the whole request.
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				       .addPropertyNode("items").addConstraintViolation();
				return false;
			}
		}

		return true;
	}
}
