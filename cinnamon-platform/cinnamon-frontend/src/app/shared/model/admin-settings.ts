
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
