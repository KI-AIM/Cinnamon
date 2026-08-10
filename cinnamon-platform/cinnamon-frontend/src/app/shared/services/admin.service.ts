import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { EmailSettings, EmailTemplate, EmailTemplateList } from "@shared/model/admin-settings";
import { UserInfo, UserRole } from "@shared/model/user";
import { catchError, Observable, of, throwError } from "rxjs";
import { environments } from "src/environments/environment";

/**
 * Service for the communication with the admin API of the platform.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class AdminService {

    constructor(
        private readonly http: HttpClient,
    ) {
    }

    /**
     * Fetches all users of the application.
     */
    public getAllUsers(): Observable<UserInfo[]> {
        return this.http.get<UserInfo[]>(this.baseUrl() + "/users");
    }

    /**
     * Adds or removes the given roles of the given user.
     *
     * @param username The name of the user to be updated.
     * @param roles The roles to be added or removed.
     * @param action If the roles should be added or removed.
     */
    public updateUserRoles(username: string, roles: UserRole[], action: 'ADD' | 'REMOVE'): Observable<UserInfo> {
        return this.http.patch<UserInfo>(this.baseUrl() + "/users/roles", {
            username: username,
            roles: roles,
            action: action,
        });
    }

    /**
     * Fetches the mail settings of the application.
     * Emits null if the mail settings have not been configured yet.
     */
    public getMailSettings(): Observable<EmailSettings | null> {
        return this.http.get<EmailSettings>(this.baseUrl() + "/settings/mail").pipe(
            catchError((error: HttpErrorResponse) => {
                // The settings have not been configured yet, which is not an error for the UI.
                if (error.status === 404) {
                    return of(null);
                }
                return throwError(() => error);
            }),
        );
    }

    /**
     * Creates or overwrites the mail settings of the application.
     *
     * @param mailSettings The new mail settings.
     * @return The updated mail settings.
     */
    public setMailSettings(mailSettings: EmailSettings): Observable<EmailSettings> {
        return this.http.put<EmailSettings>(this.baseUrl() + "/settings/mail", mailSettings);
    }

    /**
     * Sends a test mail to the given address using the configured mail settings.
     *
     * @param mailAddress The address the test mail is sent to.
     */
    public sendTestMail(mailAddress: string): Observable<void> {
        return this.http.post<void>(this.baseUrl() + "/settings/mail/test", {
            mailAddress: mailAddress,
        });
    }

    /**
     * Fetches all mail templates together with the languages that can be configured for them.
     */
    public getEmailTemplates(): Observable<EmailTemplateList> {
        return this.http.get<EmailTemplateList>(this.baseUrl() + "/settings/mail/templates");
    }

    /**
     * Creates a new mail template.
     *
     * @param template The template to create.
     * @return The created template.
     */
    public createEmailTemplate(template: EmailTemplate): Observable<EmailTemplate> {
        return this.http.post<EmailTemplate>(this.baseUrl() + "/settings/mail/templates", template);
    }

    /**
     * Updates the mail template with the given ID.
     * The template contains the complete content, so languages that are not part of it are removed.
     *
     * @param id The ID of the template to update.
     * @param template The new values of the template.
     * @return The updated template.
     */
    public updateEmailTemplate(id: number, template: EmailTemplate): Observable<EmailTemplate> {
        return this.http.put<EmailTemplate>(this.baseUrl() + "/settings/mail/templates/" + id, template);
    }

    /**
     * Deletes the mail template with the given ID.
     *
     * @param id The ID of the template to delete.
     */
    public deleteEmailTemplate(id: number): Observable<void> {
        return this.http.delete<void>(this.baseUrl() + "/settings/mail/templates/" + id);
    }

    private baseUrl(): string {
        return environments.apiUrl + "/api/admin";
    }

}
