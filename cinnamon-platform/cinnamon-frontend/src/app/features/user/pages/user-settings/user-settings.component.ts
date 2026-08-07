import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { TitleService } from "@core/services/title-service.service";
import { AppConfig, AppConfigService } from "@shared/services/app-config.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { UserService } from "@shared/services/user.service";
import { Observable, tap } from "rxjs";

@Component({
  selector: 'app-user-settings',
  standalone: false,
  templateUrl: './user-settings.component.html',
  styleUrl: './user-settings.component.less'
})
export class UserSettingsComponent implements OnInit {

    private confirmDeletionForm: FormGroup;
    private deletionDialog: MatDialogRef<MatDialog> | null = null;

    /**
     * Message to show if the deletion failed.
     * Null if no error occurred.
     */
    protected deletionError: string | null = null;

    protected updateUsernameForm: FormGroup;
    private updateUsernameDialog: MatDialogRef<MatDialog> | null = null;

    protected updatePasswordForm: FormGroup;
    private updatePasswordDialog: MatDialogRef<MatDialog> | null = null;

    protected appConfig$: Observable<AppConfig>;

    public constructor(
        private readonly appConfigService: AppConfigService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly formBuilder: FormBuilder,
        private readonly matDialog: MatDialog,
        private readonly notificationService: NotificationService,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.titleService.setPageTitle("Account Settings");
    }

    ngOnInit(): void {
        this.confirmDeletionForm = this.formBuilder.group({
            username: ['', {validators: [Validators.required]}],
            password: ['', {validators: [Validators.required]}],
        });

        this.updateUsernameForm = this.formBuilder.group({
            newUsername: ['', {validators: [Validators.required]}],
            currentPassword: ['', {validators: [Validators.required]}],
        });

        this.appConfig$ = this.appConfigService.appConfig$.pipe(
            tap(appConfig => {
                this.updatePasswordForm = this.formBuilder.group({
                    currentPassword: ['', {validators: [Validators.required]}],
                    newPassword: ['', {validators: [Validators.required, this.userService.passwordRequirementsValidator(appConfig.passwordRequirements)]}],
                    newPasswordRepeated: ['', {validators: [Validators.required]}],
                }, {validators: [this.userService.passwordMatchesValidator("newPassword", "newPasswordRepeated")]});
            }),
        );
    }

    protected get username(): string {
        return this.userService.getUser().userInfo.username;
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ changeUsername ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    protected get changeUsernameNewUsernameControl(): FormControl<string> {
        return this.updateUsernameForm.get('newUsername') as FormControl<string>;
    }

    protected get changeUsernameCurrentPasswordControl(): FormControl<string> {
        return this.updateUsernameForm.get('currentPassword') as FormControl<string>;
    }

    protected openChangeUsernameDialog(dialog: any): void {
        this.updateUsernameDialog = this.matDialog.open(dialog, {
            width: '500px',
            autoFocus: false,
            disableClose: false,
            hasBackdrop: true,
        });

        this.updateUsernameDialog.afterClosed().subscribe(() => {
            this.updateUsernameDialog = null;
            this.updateUsernameForm.reset();
        });
    }

    protected closeChangeUsernameDialog(): void {
        if (this.updateUsernameDialog) {
            this.updateUsernameDialog.close();
        }
    }

    protected changeUsername(): void {
        this.userService.updateUsername(
            this.changeUsernameNewUsernameControl.value,
            this.changeUsernameCurrentPasswordControl.value
        ).subscribe({
            next: () => {
                this.closeChangeUsernameDialog();

                const notification = new AppNotification("Username changed successfully.", 'success');
                notification.user = this.username;
                this.notificationService.addNotification(notification);
            },
            error: e => {
                this.closeChangeUsernameDialog();

                this.errorHandlingService.addError(e);
            },
        });
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ changePassword ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    protected get currentPasswordControl(): FormControl<string> {
        return this.updatePasswordForm.get('currentPassword') as FormControl<string>;
    }

    protected get newPasswordControl(): FormControl<string> {
        return this.updatePasswordForm.get('newPassword') as FormControl<string>;
    }

    protected get newPasswordRepeatedControl(): FormControl<string> {
        return this.updatePasswordForm.get('newPasswordRepeated') as FormControl<string>;
    }

    protected openChangePasswordDialog(dialog: any): void {
        this.updatePasswordDialog = this.matDialog.open(dialog, {
            width: '500px',
            autoFocus: false,
            disableClose: false,
            hasBackdrop: true,
        });

        this.updatePasswordDialog.afterClosed().subscribe(() => {
            this.updatePasswordDialog = null;
            this.updatePasswordForm.reset();
        });
    }

    protected closeChangePasswordDialog(): void {
        if (this.updatePasswordDialog) {
            this.updatePasswordDialog.close();
        }
    }

    protected changePassword() {
        this.userService.updatePassword(
            this.currentPasswordControl.value,
            this.newPasswordControl.value,
            this.newPasswordRepeatedControl.value
        ).subscribe({
            next: () => {
                this.closeChangePasswordDialog();

                const notification = new AppNotification("Password changed successfully.", 'success');
                notification.user = this.username;
                this.notificationService.addNotification(notification);
            },
            error: e => {
                this.closeChangePasswordDialog();

                this.errorHandlingService.addError(e);
            },
        });
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ deleteAccount ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    protected get confirmDeletionPasswordControl(): FormControl<string> {
        return this.confirmDeletionForm.get('password') as FormControl<string>;
    }

    protected get confirmDeletionUsernameControl(): FormControl<string> {
        return this.confirmDeletionForm.get('username') as FormControl<string>;
    }

    protected openDeleteAccountDialog(dialog: any): void {
        this.deletionError = null;
        this.deletionDialog = this.matDialog.open(dialog, {
            width: '500px',
            autoFocus: false,
            disableClose: false,
            hasBackdrop: true,
        });

        this.deletionDialog.afterClosed().subscribe(() => {
            this.deletionDialog = null;
            this.confirmDeletionForm.reset();
        });
    }

    protected closeDeleteAccountDialog(): void {
        if (this.deletionDialog) {
            this.deletionDialog.close();
        }
    }

    protected deleteAccount(email: string, password: string) {
        this.deletionError = null;

        this.userService.delete(email, password).subscribe({
            next: () => {
                this.userService.logout('delete');
                this.closeDeleteAccountDialog();

                const notification = new AppNotification("Account deleted successfully.", 'success');
                notification.user = this.username;
                this.notificationService.addNotification(notification);
            },
            error: e => {
                this.deletionError = e.error.errorMessage;
            },
        });
    }

}
