import { Component, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from "@angular/forms";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { EmailTemplate, EmailTemplateItem, SupportedLanguage } from "@shared/model/admin-settings";
import { AdminService } from "@shared/services/admin.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { UserService } from "@shared/services/user.service";
import { Observable } from "rxjs";

/**
 * Settings for the mail templates of the application.
 * The languages that can be configured are delivered by the backend, so a language added there is available in this
 * component without further changes.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-mail-templates',
    standalone: false,
    templateUrl: './mail-templates.component.html',
    styleUrl: './mail-templates.component.less'
})
export class MailTemplatesComponent implements OnInit {

    /**
     * All languages that can be configured for a template.
     */
    protected languages: SupportedLanguage[] = [];

    /**
     * All templates stored in the backend.
     */
    protected templates: EmailTemplate[] = [];

    /**
     * Form containing the name of the template and its content.
     * The content contains one group per language the template has been configured for.
     */
    protected form: FormGroup;

    /**
     * Control of the dropdown for adding a language to the template.
     * The dropdown only triggers the action and therefore never keeps a value.
     */
    protected addLanguageControl: FormControl<string | null>;

    /**
     * ID of the template that is currently edited.
     * Null if no template is selected.
     * -1 if a new template is created instead of an existing one being edited.
     */
    protected selectedTemplateId: number | null = null;

    /**
     * Index of the tab of the language that is currently edited.
     */
    protected selectedTabIndex: number = 0;

    protected loading: boolean = true;
    protected saving: boolean = false;

    private deletionDialog: MatDialogRef<MatDialog> | null = null;

    constructor(
        private readonly adminService: AdminService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly formBuilder: FormBuilder,
        private readonly matDialog: MatDialog,
        private readonly notificationService: NotificationService,
        private readonly userService: UserService,
    ) {
    }

    public ngOnInit(): void {
        this.addLanguageControl = this.formBuilder.control(null);
        this.buildForm();
        this.loadTemplates(null);
        this.updateLanguageEnabled();
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ template ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    protected get nameControl(): FormControl<string> {
        return this.form.get('name') as FormControl<string>;
    }

    /**
     * If a new template is created instead of an existing one being edited.
     * @protected
     */
    protected get creatingTemplate(): boolean {
        return this.selectedTemplateId === -1;
    }

    /**
     * Name of the template that is currently edited as it is stored in the backend.
     * @protected
     */
    protected get selectedTemplateName(): string {
        return this.templates.find(template => template.id === this.selectedTemplateId)?.name ?? '';
    }

    /**
     * Selects the template with the given ID for editing.
     * Unsaved changes of the previously selected template are discarded.
     *
     * @param id The ID of the template or null to create a new one.
     * @protected
     */
    protected selectTemplate(id: number | null): void {
        if (this.selectedTemplateId === -1) {
            // Remove the unsaved new template from the list of templates.
            this.templates = this.templates.filter(template => template.id !== -1);
        }

        const template = this.templates.find(candidate => candidate.id === id) ?? null;

        this.selectedTemplateId = template?.id ?? null;
        this.applyTemplate(template);
    }

    protected addTemplate(): void {
        // Add empty template for creation
        const templateNew = new EmailTemplate();
        templateNew.id = -1;
        templateNew.name = 'New Template';
        this.templates.push(templateNew);

        this.selectTemplate(-1);
    }

    /**
     * Saves the currently edited template in the backend.
     * A template without an ID is created, an existing one is updated.
     * @protected
     */
    protected saveTemplate(): void {
        if (!this.form.valid || this.saving) {
            return;
        }

        this.saving = true;
        const template = this.createTemplate();

        const request: Observable<EmailTemplate> = (this.selectedTemplateId == null || this.selectedTemplateId === -1)
            ? this.adminService.createEmailTemplate(template)
            : this.adminService.updateEmailTemplate(this.selectedTemplateId, template);

        request.subscribe({
            next: value => {
                this.saving = false;
                this.notify("Mail template '" + value.name + "' saved successfully.");
                this.loadTemplates(value.id);
            },
            error: e => {
                this.saving = false;
                this.errorHandlingService.addError(e);
            },
        });
    }

    /**
     * Deletes the currently edited template in the backend.
     * @protected
     */
    protected deleteTemplate(): void {
        if (this.selectedTemplateId === null) {
            return;
        }

        const name = this.selectedTemplateName;

        this.adminService.deleteEmailTemplate(this.selectedTemplateId).subscribe({
            next: () => {
                this.closeDeleteTemplateDialog();
                this.notify("Mail template '" + name + "' deleted successfully.");
                this.loadTemplates(null);
            },
            error: e => {
                this.closeDeleteTemplateDialog();
                this.errorHandlingService.addError(e);
            },
        });
    }

    protected openDeleteTemplateDialog(dialog: any): void {
        this.deletionDialog = this.matDialog.open(dialog, {
            width: '500px',
            autoFocus: false,
            disableClose: false,
            hasBackdrop: true,
        });

        this.deletionDialog.afterClosed().subscribe(() => {
            this.deletionDialog = null;
        });
    }

    protected closeDeleteTemplateDialog(): void {
        if (this.deletionDialog) {
            this.deletionDialog.close();
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ languages ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * All languages the template is configured for, each of them presented in its own tab.
     * The languages are ordered like the languages delivered by the backend so that the tabs do not change their
     * position when a language is added.
     * @protected
     */
    protected get addedLanguages(): SupportedLanguage[] {
        return this.languages.filter(language => this.isLanguageAdded(language.name));
    }

    /**
     * All languages that can still be added to the template.
     * @protected
     */
    protected get availableLanguages(): SupportedLanguage[] {
        return this.languages.filter(language => !this.isLanguageAdded(language.name));
    }

    /**
     * Returns the form group containing the content of the given language.
     *
     * @param language The name of the language.
     * @protected
     */
    protected languageGroup(language: string): FormGroup {
        return this.itemsGroup.get(language) as FormGroup;
    }

    /**
     * Adds the given language to the template and selects its tab.
     * The content is stored in the backend once the template is saved.
     *
     * @param language The name of the language to add.
     * @protected
     */
    protected addLanguage(language: string | null): void {
        if (language === null || this.isLanguageAdded(language)) {
            return;
        }

        this.itemsGroup.addControl(language, this.createLanguageGroup());
        this.form.markAsDirty();
        this.selectedTabIndex = this.addedLanguages.findIndex(added => added.name === language);

        this.addLanguageControl.setValue(null);

        this.updateLanguageEnabled();
    }

    /**
     * Removes the given language from the template.
     * The content is deleted in the backend once the template is saved.
     *
     * @param language The name of the language to remove.
     * @protected
     */
    protected removeLanguage(language: string): void {
        if (!this.isLanguageAdded(language)) {
            return;
        }

        this.itemsGroup.removeControl(language);
        this.form.markAsDirty();

        // Keeps the selection inside the remaining tabs.
        this.selectedTabIndex = Math.max(0, Math.min(this.selectedTabIndex, this.addedLanguages.length - 1));

        this.updateLanguageEnabled();
    }

    protected updateLanguageEnabled(): void {
        if (this.availableLanguages.length === 0) {
            this.addLanguageControl.disable();
        } else {
            this.addLanguageControl.enable();
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ internal ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Loads all templates and supported languages from the backend and selects the template with the given ID.
     *
     * @param selectedId The ID of the template to select. If it does not exist, the first template is selected.
     * @private
     */
    private loadTemplates(selectedId: number | null): void {
        this.loading = true;

        this.adminService.getEmailTemplates().subscribe({
            next: value => {
                this.loading = false;
                this.languages = value.languages;
                this.templates = value.templates;

                const template = this.templates.find(candidate => candidate.id === selectedId) ?? this.templates[0];
                this.selectTemplate(template?.id ?? null);
            },
            error: e => {
                this.loading = false;
                this.errorHandlingService.addError(e);
            },
        });
    }

    /**
     * Creates the form. The content of the languages is added when a template is applied.
     * @private
     */
    private buildForm(): void {
        this.form = this.formBuilder.group({
            name: ['', {validators: [Validators.required]}],
            items: this.formBuilder.group({}),
        }, {validators: [this.configuredLanguageValidator]});
    }

    /**
     * Applies the given template to the form.
     * The template gets a tab for every language it is configured for.
     *
     * @param template The template or null to start a new template.
     * @private
     */
    private applyTemplate(template: EmailTemplate | null): void {
        const items = this.itemsGroup;
        for (const language of Object.keys(items.controls)) {
            items.removeControl(language);
        }

        for (const item of template?.items ?? []) {
            const group = this.createLanguageGroup();
            group.patchValue({subject: item.subject, body: item.body});
            items.addControl(item.language, group);
        }

        // A new template starts with the first supported language so that content can be entered right away.
        if (this.addedLanguages.length === 0 && this.languages.length > 0) {
            items.addControl(this.languages[0].name, this.createLanguageGroup());
        }

        this.form.patchValue({name: template?.name ?? ''});
        this.selectedTabIndex = 0;
        this.addLanguageControl.setValue(null);

        this.form.markAsPristine();
        this.form.markAsUntouched();
    }

    /**
     * Creates the request for the currently edited template.
     * Every language that has been added is part of the request, all other languages are removed by the backend.
     *
     * @private
     */
    private createTemplate(): EmailTemplate {
        const template = new EmailTemplate();
        template.id = this.selectedTemplateId;
        template.name = this.nameControl.value;
        template.items = this.addedLanguages.map(language => {
            const value = this.languageGroup(language.name).value;

            const item = new EmailTemplateItem();
            item.language = language.name;
            item.subject = value.subject;
            item.body = value.body;
            return item;
        });

        return template;
    }

    /**
     * Creates the form group for the content of a single language.
     * A language that has been added has to be filled in completely because it is stored with the template.
     *
     * @private
     */
    private createLanguageGroup(): FormGroup {
        return this.formBuilder.group({
            subject: ['', {validators: [Validators.required]}],
            body: ['', {validators: [Validators.required]}],
        });
    }

    private get itemsGroup(): FormGroup {
        return this.form.get('items') as FormGroup;
    }

    private isLanguageAdded(language: string): boolean {
        return this.itemsGroup.get(language) !== null;
    }

    /**
     * Validates that the template is configured for at least one language.
     *
     * @private
     */
    private readonly configuredLanguageValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
        const items = control.get('items') as FormGroup | null;

        if (items === null) {
            return null;
        }

        return Object.keys(items.controls).length > 0 ? null : {noLanguageConfigured: true};
    };

    private notify(message: string): void {
        const notification = new AppNotification(message, 'success');
        notification.user = this.userService.getUser().userInfo.username;
        this.notificationService.addNotification(notification);
    }

}
