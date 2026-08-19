import { HttpClient } from "@angular/common/http";
import { Component, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { NotificationService } from "@core/services/notification.service";
import { TitleService } from "@core/services/title-service.service";
import { EmailTemplate, EmailTemplateItem, EmailTemplateList, SupportedLanguage } from "@shared/model/admin-settings";
import { UserInvitationInfo, UserInvitationStatus, UserRole } from "@shared/model/user";
import { AdminService } from "@shared/services/admin.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { BehaviorSubject, combineLatest, map, Observable, shareReplay, tap } from "rxjs";
import { environments } from "src/environments/environment";

@Component({
    selector: 'app-user-invitation-form',
    standalone: false,
    templateUrl: './user-invitation-form.component.html',
    styleUrl: './user-invitation-form.component.less'
})
export class UserInvitationFormComponent implements OnInit {

    protected readonly UserInvitationStatus = UserInvitationStatus;

    protected invitationForm: FormGroup;
    protected currentInvitation$: Observable<UserInvitationInfo>;
    private currentInvitation: BehaviorSubject<UserInvitationInfo>;
    protected mailTemplates$: Observable<EmailTemplateList>;

    /**
     * All mail templates and supported languages as last delivered by {@link mailTemplates$}.
     * Kept around so the currently selected template/language can be resolved synchronously.
     */
    private emailTemplateList: EmailTemplateList = {languages: [], templates: []};

    /**
     * ID of the template currently selected in the "E-Mail Template" dropdown.
     * Null if no template is selected, meaning the invitation must use the custom mail content.
     */
    protected selectedTemplateId: number | null = null;

    /**
     * Language of the template item currently selected in the "Language" dropdown.
     */
    protected selectedLanguage: string | null = null;

    /**
     * If the custom subject/body inputs should be used and sent instead of the selected template item.
     */
    protected useCustomMail: boolean = false;

    public constructor(
        private readonly adminService: AdminService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly formBuilder: FormBuilder,
        private readonly httpClient: HttpClient,
        private readonly notificationService: NotificationService,
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Administration - Invite User");
        this.currentInvitation = new BehaviorSubject<UserInvitationInfo>(this.emptyInvitation());
    }

    public ngOnInit(): void {
        // Set observables
        this.mailTemplates$ = this.adminService.getEmailTemplates().pipe(
            tap(emailTemplateList => this.emailTemplateList = emailTemplateList),
            shareReplay(1),
        );

        // Combined so the template/language selection can be resolved as soon as both the invitation and the
        // templates are available, regardless of which of the two resolves first.
        this.currentInvitation$ = combineLatest([this.currentInvitation, this.mailTemplates$]).pipe(
            tap(([invitation]) => {
                this.invitationForm = this.createForm(invitation);
                this.initializeMailSelection(invitation);
            }),
            map(([invitation]) => invitation),
        );

        // Initialize the current invitation based on the route parameter
        const invitationId = this.readInvitationId();
        if (!this.isInvitationNew(invitationId)) {
            this.fetchCurrentInvitation(invitationId).pipe(
                tap(invitation => {
                    this.currentInvitation.next(invitation);
                }),
            ).subscribe({
                error: (error) => {
                    this.errorHandlingService.addError(error, "Failed to load invitation.");
                }
            });
        }
    }

    protected createOrUpdateInvitation(): void {
        const invitationId = this.readInvitationId();
        if (this.isInvitationNew(invitationId)) {
            this.createInvitation();
        } else {
            this.saveInvitation(invitationId);
        }
    }

    protected createInvitation(): void {
        this.httpClient.post<UserInvitationInfo>(this.getBaseUrl(), this.buildInvitationRequest()).subscribe({
            next: (invitation) => {
                this.currentInvitation.next(invitation);
                this.router.navigate(['/admin/invitation', invitation.id]);
                this.notificationService.addNotificationSuccess("Invitation saved successfully.");
            },
            error: (error) => {
                this.errorHandlingService.addError(error, "Failed to save invitation.");
            }
        });
    }

    protected saveInvitation(id: string): void {
        this.httpClient.put<UserInvitationInfo>(this.getBaseUrl() + '/' + id, this.buildInvitationRequest()).subscribe({
            next: (invitation) => {
                this.currentInvitation.next(invitation);
                this.notificationService.addNotificationSuccess("Invitation updated successfully.");
            },
            error: (error) => {
                this.errorHandlingService.addError(error, "Failed to update invitation.");
            }
        });
    }

    protected sendInvitation(id: string): void {
        this.httpClient.post<UserInvitationInfo>(this.getBaseUrl() + '/' + id + '/send', this.buildInvitationRequest()).subscribe({
            next: (invitation) => {
                this.currentInvitation.next(invitation);
                this.notificationService.addNotificationSuccess("Invitation sent successfully.");
            },
            error: (error) => {
                this.errorHandlingService.addError(error, "Failed to send invitation.");
            }
        });
    }

    protected revokeInvitation(invitationId: string): void {
        this.httpClient.post<UserInvitationInfo>(this.getBaseUrl() + '/' + invitationId + '/revoke', {invitationId}).subscribe({
            next: (invitation) => {
                this.currentInvitation.next(invitation);
                this.notificationService.addNotificationSuccess("Invitation revoked successfully.");
            },
            error: (error) => {
                this.errorHandlingService.addError(error, "Failed to revoke invitation.");
            }
        });
    }

    /**
     * The control containing the custom body, typed for {@link app-placeholder-input}.
     * @protected
     */
    protected get emailCustomBodyControl(): FormControl<string | null> {
        return this.invitationForm.get('emailCustomBody') as FormControl<string | null>;
    }

    protected get rolesArray(): FormArray<FormControl<string>> {
        return this.invitationForm.get('userRoles') as FormArray<FormControl<string>>;
    }

    protected isRoleSelected(role: string): boolean {
        return this.rolesArray.controls.some(control => control.value === role);
    }

    protected toggleRole(role: string, checked: boolean): void {
        if (checked) {
            if (!this.isRoleSelected(role)) {
                this.rolesArray.push(new FormControl(role, {nonNullable: true}));
            }
            return;
        }

        const index = this.rolesArray.controls.findIndex(control => control.value === role);
        if (index !== -1) {
            this.rolesArray.removeAt(index);
        }
    }

    private createForm(initialValue: UserInvitationInfo): FormGroup {
        const disabled = initialValue.status === UserInvitationStatus.ACCEPTED;
        return this.formBuilder.group({
            email: [{value: initialValue.email, disabled: disabled},
                {nonNullable: true, validators: [Validators.required, Validators.email]}],
            userRoles: this.formBuilder.array((initialValue.userRoles ?? []).map(role => this.formBuilder.control({
                value: role,
                disabled: disabled
            }))),
            emailCustomSubject: [{value: initialValue.emailCustomSubject, disabled: disabled}],
            emailCustomBody: [{value: initialValue.emailCustomBody, disabled: disabled}],
        });
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ mail template ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * All templates that can be selected in the "E-Mail Template" dropdown.
     * @protected
     */
    protected get availableTemplates(): EmailTemplate[] {
        return this.emailTemplateList.templates;
    }

    /**
     * Languages the currently selected template has content for.
     * Empty if no template is selected, since there is nothing to choose a language of.
     * @protected
     */
    protected get availableLanguages(): SupportedLanguage[] {
        const template = this.selectedTemplate;
        if (template === null) {
            return [];
        }

        return this.emailTemplateList.languages.filter(language =>
            template.items.some(item => item.language === language.name));
    }

    /**
     * Selects the template with the given ID in the "E-Mail Template" dropdown and picks its first available
     * language.
     *
     * @param templateId The ID of the template to select.
     * @protected
     */
    protected selectTemplate(templateId: number | null): void {
        this.selectedTemplateId = templateId;
        this.toggleUseCustomMail(false);
        this.selectLanguage(this.selectedTemplate?.items[0]?.language ?? null);
    }

    /**
     * Selects the given language in the "Language" dropdown and loads the content of the resulting template item
     * into the subject/body inputs so it can be previewed or, if the custom mail is used, edited further.
     *
     * @param language The name of the language to select.
     * @protected
     */
    protected selectLanguage(language: string | null): void {
        this.selectedLanguage = language;
        this.toggleUseCustomMail(false);

        const item = this.selectedTemplateItem;
        if (item !== null) {
            this.invitationForm.patchValue({emailCustomSubject: item.subject, emailCustomBody: item.body});
        }
    }

    /**
     * Enables or disables editing of the custom subject/body inputs.
     * Disabled while a template is used unmodified so the inputs only preview the template's content.
     * Enabling clears the template/language selection since a custom mail is not based on a template.
     * The content of the previously selected template item is kept as a starting point for editing.
     *
     * @param useCustomMail If the custom mail content should be used and made editable.
     * @protected
     */
    protected toggleUseCustomMail(useCustomMail: boolean): void {
        this.useCustomMail = useCustomMail;

        if (useCustomMail) {
            this.selectedTemplateId = null;
            this.selectedLanguage = null;
        }

        this.updateCustomMailControls();
    }

    /**
     * The template currently selected in the "E-Mail Template" dropdown, or null if none is selected.
     * @private
     */
    private get selectedTemplate(): EmailTemplate | null {
        return this.availableTemplates.find(template => template.id === this.selectedTemplateId) ?? null;
    }

    /**
     * The template item resulting from the currently selected template and language, or null if no template is
     * selected or the selected template has no content for the selected language.
     * @private
     */
    private get selectedTemplateItem(): EmailTemplateItem | null {
        const template = this.selectedTemplate;
        if (template === null) {
            return null;
        }

        return template.items.find(item => item.language === this.selectedLanguage) ?? null;
    }

    /**
     * Initializes the template/language/custom-mail selection based on the given invitation.
     * If the invitation uses a template item, the template and language it belongs to are looked up so the
     * dropdowns can be preselected. Otherwise, the invitation is treated as using a custom mail.
     *
     * @param invitation The invitation the selection is initialized from.
     * @private
     */
    private initializeMailSelection(invitation: UserInvitationInfo): void {
        const match = invitation.emailTemplateItemId == null
            ? null
            : this.findTemplateOfItem(invitation.emailTemplateItemId);

        this.selectedTemplateId = match?.template.id ?? null;
        this.selectedLanguage = match?.item.language ?? null;
        this.useCustomMail = match === null;

        this.updateCustomMailControls();
    }

    /**
     * Finds the template that contains the template item with the given ID.
     *
     * @param itemId The ID of the template item to look for.
     * @private
     */
    private findTemplateOfItem(itemId: number): { template: EmailTemplate, item: EmailTemplateItem } | null {
        for (const template of this.availableTemplates) {
            const item = template.items.find(candidate => candidate.id === itemId);
            if (item) {
                return {template, item};
            }
        }
        return null;
    }

    /**
     * Enables and requires the custom subject/body inputs while the custom mail is used, disables them otherwise
     * so they only preview the selected template item's content.
     * @private
     */
    private updateCustomMailControls(): void {
        const subjectControl = this.invitationForm.get('emailCustomSubject') as FormControl;
        const bodyControl = this.invitationForm.get('emailCustomBody') as FormControl;

        if (this.useCustomMail) {
            if (this.currentInvitation.value.status !== UserInvitationStatus.ACCEPTED) {
                subjectControl.enable();
                bodyControl.enable();
            }
            subjectControl.setValidators([Validators.required]);
            bodyControl.setValidators([Validators.required]);
        } else {
            subjectControl.disable();
            bodyControl.disable();
            subjectControl.clearValidators();
            bodyControl.clearValidators();
        }
        subjectControl.updateValueAndValidity();
        bodyControl.updateValueAndValidity();
    }

    /**
     * If the invitation can be saved: the form must be valid, and either the custom mail is used or a template
     * item has been resolved from the selected template and language.
     * @protected
     */
    protected get canSaveInvitation(): boolean {
        if (this.invitationForm == null || this.invitationForm.invalid) {
            return false;
        }

        return this.currentInvitation.value.status !== UserInvitationStatus.ACCEPTED
            && (this.useCustomMail || this.selectedTemplateItem !== null);
    }

    /**
     * Builds the request body for creating, updating or sending the invitation.
     * Only the custom subject/body or the resolved template item ID is sent, never both, so the backend cannot
     * pick up a custom mail alongside a template item that is no longer selected (or vice versa).
     * @private
     */
    private buildInvitationRequest(): Partial<UserInvitationInfo> {
        const value = this.invitationForm.getRawValue();

        return {
            ...value,
            emailTemplateItemId: this.useCustomMail ? null : this.selectedTemplateItem?.id ?? null,
            emailCustomSubject: this.useCustomMail ? value.emailCustomSubject : null,
            emailCustomBody: this.useCustomMail ? value.emailCustomBody : null,
        };
    }

    private fetchCurrentInvitation(id: string): Observable<UserInvitationInfo> {
        return this.httpClient.get<UserInvitationInfo>(this.getBaseUrl() + '/' + id);
    }

    private emptyInvitation(): UserInvitationInfo {
        const invitation = new UserInvitationInfo();
        invitation.status = UserInvitationStatus.NOT_CREATED;
        invitation.userRoles = [];
        return invitation;
    }

    private readInvitationId(): string {
        return this.route.snapshot.params['invitationId'];
    }

    private isInvitationNew(invitationId: string): boolean {
        return invitationId == null || invitationId === 'new';
    }

    private getBaseUrl(): string {
        return environments.apiUrl + '/api/admin/invitations';
    }

    protected readonly UserRole = UserRole;
    protected readonly Object = Object;
}
