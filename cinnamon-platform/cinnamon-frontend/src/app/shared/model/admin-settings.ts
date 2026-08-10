
/**
 * Mail settings of the application.
 * Used both for reading and writing the settings.
 */
export class EmailSettings {
    mailHost: string;
    mailPort: number;
    mailTLS: boolean;
    mailSMTPAuth: boolean;
    mailUsername: string;
    mailSender: string;

    /**
     * Password of the application mailer.
     * Never part of a response.
     * Null or empty when updating the settings keeps the currently stored password.
     */
    mailPassword: string | null;

    /**
     * If a password has been configured.
     * Only part of a response.
     */
    mailPasswordSet: boolean;
}

/**
 * A language that can be configured for a mail template.
 * Delivered by the backend so that the UI does not have to know the supported languages.
 */
export class SupportedLanguage {
    /**
     * Name of the language as used in the API.
     */
    name: string;

    /**
     * Name of the language as presented to the user.
     */
    displayName: string;
}

/**
 * The content of a mail template for a single language.
 */
export class EmailTemplateItem {
    language: string;
    subject: string;
    body: string;
}

/**
 * A mail template with its content in all configured languages.
 * Used both for reading and writing a template.
 */
export class EmailTemplate {
    /**
     * ID of the template.
     * Null for a template that has not been created yet.
     */
    id: number | null;

    /**
     * Unique name identifying the template.
     */
    name: string;

    /**
     * The content of the template.
     * Contains at most one entry per language, languages without content are not part of the list.
     */
    items: EmailTemplateItem[];
}

/**
 * All mail templates of the application and the languages that can be configured for them.
 */
export class EmailTemplateList {
    languages: SupportedLanguage[];
    templates: EmailTemplate[];
}
