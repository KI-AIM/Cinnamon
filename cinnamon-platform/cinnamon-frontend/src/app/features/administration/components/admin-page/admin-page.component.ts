import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { MatCheckbox } from "@angular/material/checkbox";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { NavigationService } from "@core/services/navigation.service";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { TitleService } from "@core/services/title-service.service";
import { EmailSettings } from "@shared/model/admin-settings";
import { NavigationKey } from "@shared/model/navigation";
import { UserInfo, UserRole } from "@shared/model/user";
import { AdminService } from "@shared/services/admin.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { UserService } from "@shared/services/user.service";
import { combineLatest, Observable, of, tap } from "rxjs";

@Component({
  selector: 'app-admin-page',
  standalone: false,
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.less'
})
export class AdminPageComponent implements OnInit {

    protected readonly userTableColumns = ['username', 'roleUser', 'roleApi', 'roleAdmin', 'roleMonitoring'];
    protected readonly UserRole = UserRole;

    protected pageData$: Observable<{
        currentUser: string,
        users: UserInfo[],
        mailSettings: EmailSettings | null,
    }>;

    protected dataSource = new MatTableDataSource();

    /**
     * Form containing the mail settings of the application.
     */
    protected mailSettingsForm: FormGroup;

    /**
     * Control for the address a test mail is sent to.
     */
    protected testMailControl: FormControl<string>;

    /**
     * If the mail settings have been configured in the backend.
     */
    protected mailSettingsConfigured: boolean = false;

    /**
     * If a password has been configured for the application mailer.
     */
    protected mailPasswordSet: boolean = false;

    protected hideMailPassword: boolean = true;
    protected savingMailSettings: boolean = false;
    protected sendingTestMail: boolean = false;

    @ViewChild(MatSort)
    protected set sort(sort: MatSort) {
        this.dataSource.sort = sort;
    }

    @ViewChild(MatPaginator)
    protected set paginator(paginator: MatPaginator) {
        this.dataSource.paginator = paginator;
    }

    constructor(
        private readonly adminService: AdminService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly formBuilder: FormBuilder,
        private readonly navigationService: NavigationService,
        private readonly notificationService: NotificationService,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.navigationService.setNavigationKey(NavigationKey.ADMIN);
        this.titleService.setPageTitle("Administration - Security");
    }

    public ngOnInit(): void {
        this.mailSettingsForm = this.formBuilder.group({
            mailHost: ['', {validators: [Validators.required]}],
            mailPort: [587, {validators: [Validators.required, Validators.min(1), Validators.max(65535)]}],
            mailTLS: [true],
            mailSMTPAuth: [true],
            mailUsername: [''],
            mailPassword: [''],
            mailSender: ['', {validators: [Validators.required, Validators.email]}],
        });

        // The credentials are only used if SMTP authentication is enabled.
        this.mailSettingsForm.get('mailSMTPAuth')!.valueChanges.subscribe(() => {
            this.updateCredentialValidators();
        });
        this.updateCredentialValidators();

        this.testMailControl = this.formBuilder.control('', {
            nonNullable: true,
            validators: [Validators.required, Validators.email],
        });

        this.pageData$ = combineLatest({
            currentUser: of(this.getCurrentUser()),
            users: this.adminService.getAllUsers(),
            mailSettings: this.adminService.getMailSettings(),
        }).pipe(
            tap(data => {
                this.dataSource.data = data.users;
                this.applyMailSettings(data.mailSettings);
            }),
        );
    }

    protected onRoleChange(username: string, role: UserRole, source: MatCheckbox): void {
        const isChecked = source.checked;

        this.adminService.updateUserRoles(username, [role], isChecked ? "ADD" : "REMOVE").subscribe({
            error: (error) => {
                this.errorHandlingService.addError(error, "Failed to update user role");
                source.checked = !isChecked;
            }
        });
    }

    protected applyFilterEvent(event: Event): void {
        const filterValue = (event.target as HTMLInputElement).value;
        this.applyFilterValue(filterValue);
    }

    protected applyFilterValue(filterValue: string): void {
        this.dataSource.filter = filterValue.trim().toLowerCase();

        if (this.dataSource.paginator) {
            this.dataSource.paginator.firstPage();
        }
    }

    protected getCurrentUser(): string {
        return this.userService.getUser().userInfo.username;
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ mailSettings ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * If SMTP authentication is enabled and the credentials are therefore required.
     * @protected
     */
    protected get smtpAuthEnabled(): boolean {
        return this.mailSettingsForm.get('mailSMTPAuth')!.value;
    }

    /**
     * If a password has to be entered.
     * A stored password does not have to be entered again.
     * @protected
     */
    protected get mailPasswordRequired(): boolean {
        return this.smtpAuthEnabled && !this.mailPasswordSet;
    }

    /**
     * Saves the mail settings in the backend.
     * @protected
     */
    protected saveMailSettings(): void {
        if (!this.mailSettingsForm.valid || this.savingMailSettings) {
            return;
        }

        this.savingMailSettings = true;

        this.adminService.setMailSettings(this.createMailSettings()).subscribe({
            next: value => {
                this.savingMailSettings = false;
                this.applyMailSettings(value);

                const notification = new AppNotification("Mail settings saved successfully.", 'success');
                notification.user = this.getCurrentUser();
                this.notificationService.addNotification(notification);
            },
            error: e => {
                this.savingMailSettings = false;
                this.errorHandlingService.addError(e);
            },
        });
    }

    /**
     * Sends a test mail to the configured address using the mail settings stored in the backend.
     * @protected
     */
    protected sendTestMail(): void {
        if (!this.testMailControl.valid || this.sendingTestMail) {
            return;
        }

        this.sendingTestMail = true;
        const mailAddress = this.testMailControl.value;

        this.adminService.sendTestMail(mailAddress).subscribe({
            next: () => {
                this.sendingTestMail = false;

                const notification = new AppNotification("Test mail has been sent to " + mailAddress + ".", 'success');
                notification.user = this.getCurrentUser();
                this.notificationService.addNotification(notification);
            },
            error: e => {
                this.sendingTestMail = false;
                this.errorHandlingService.addError(e);
            },
        });
    }

    /**
     * Applies the given mail settings to the form.
     * The password is never part of the settings and is therefore always cleared.
     *
     * @param mailSettings The mail settings or null if they have not been configured yet.
     * @private
     */
    private applyMailSettings(mailSettings: EmailSettings | null): void {
        this.mailSettingsConfigured = mailSettings != null;
        this.mailPasswordSet = mailSettings != null && mailSettings.mailPasswordSet;

        if (mailSettings != null) {
            this.mailSettingsForm.patchValue({
                mailHost: mailSettings.mailHost,
                mailPort: mailSettings.mailPort,
                mailTLS: mailSettings.mailTLS,
                mailSMTPAuth: mailSettings.mailSMTPAuth,
                mailUsername: mailSettings.mailUsername,
                mailSender: mailSettings.mailSender,
            });
        }

        this.mailSettingsForm.patchValue({mailPassword: ''});
        this.updateCredentialValidators();
        this.mailSettingsForm.markAsPristine();
    }

    /**
     * Updates the validators of the credentials.
     * The username is only required if SMTP authentication is enabled and the password additionally only if none
     * has been stored yet.
     *
     * @private
     */
    private updateCredentialValidators(): void {
        const usernameControl = this.mailSettingsForm.get('mailUsername')!;
        usernameControl.setValidators(this.smtpAuthEnabled ? [Validators.required] : []);
        usernameControl.updateValueAndValidity({emitEvent: false});

        const passwordControl = this.mailSettingsForm.get('mailPassword')!;
        passwordControl.setValidators(this.mailPasswordRequired ? [Validators.required] : []);
        passwordControl.updateValueAndValidity({emitEvent: false});
    }

    /**
     * Creates the request for updating the mail settings based on the form.
     * An empty password is sent as null so that the stored password is kept.
     *
     * @private
     */
    private createMailSettings(): EmailSettings {
        const value = this.mailSettingsForm.getRawValue();

        const mailSettings = new EmailSettings();
        mailSettings.mailHost = value.mailHost;
        mailSettings.mailPort = value.mailPort;
        mailSettings.mailTLS = value.mailTLS;
        mailSettings.mailSMTPAuth = value.mailSMTPAuth;
        mailSettings.mailUsername = value.mailUsername;
        mailSettings.mailPassword = value.mailPassword ? value.mailPassword : null;
        mailSettings.mailSender = value.mailSender;

        return mailSettings;
    }
}
