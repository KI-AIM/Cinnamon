import { Component } from '@angular/core';
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { TitleService } from "@core/services/title-service.service";
import { UserService } from "@shared/services/user.service";

@Component({
  selector: 'app-user-settings',
  standalone: false,
  templateUrl: './user-settings.component.html',
  styleUrl: './user-settings.component.less'
})
export class UserSettingsComponent {

    private deletionDialog: MatDialogRef<MatDialog> | null = null;

    /**
     * Message to show if the deletion failed.
     * Null if no error occurred.
     */
    protected deletionError: string | null = null;

    /**
     * If the password should be hidden by dots.
     */
    protected hidePassword: boolean = true;

    public constructor(
        private readonly matDialog: MatDialog,
        private readonly notificationService: NotificationService,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.titleService.setPageTitle("Account Settings");
    }

    protected get username(): string {
        return this.userService.getUser().username;
    }

    protected openDeleteAccountDialog(dialog: any): void {
        this.deletionError = null;
        this.deletionDialog = this.matDialog.open(dialog, {
            width: '500px',
            disableClose: false,
            hasBackdrop: true,
        });

        this.deletionDialog.afterClosed().subscribe(() => {
            this.deletionDialog = null;
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
