import { Component, OnInit, TemplateRef } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { map, Observable } from "rxjs";

/**
 * Component for the notification center.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-notification-center',
  standalone: false,
  templateUrl: './notification-center.component.html',
  styleUrl: './notification-center.component.less'
})
export class NotificationCenterComponent implements OnInit {

    protected notifications$: Observable<AppNotification[]>;
    protected numberUnreadNotifications$: Observable<number>;

    public constructor(
        private readonly matDialog: MatDialog,
        protected readonly notificationService: NotificationService,
    ) { }

    public ngOnInit(): void {
        this.notifications$ = this.notificationService.notifications$().pipe(
            map(value => {
                return value.slice().reverse();
            }),
        );
        this.numberUnreadNotifications$ = this.notificationService.numberUnreadNotifications$();
    }

    /**
     * Opens the notification center dialog.
     *
     * @param dialog The dialog template.
     * @param trigger The anchor element that triggered the dialog.
     */
    protected openCenter(dialog: TemplateRef<MatDialog>, trigger: HTMLAnchorElement) {
        const rect = trigger.getBoundingClientRect();

        const dialogRef = this.matDialog.open(dialog, {
            width: '300px',
            disableClose: false,
            hasBackdrop: true,
            position: {
                top: `${rect.bottom}px`,
                right: `${window.innerWidth - rect.left}px`,
            }
        });

        dialogRef.afterClosed().subscribe(() => {
            // Mark all notifications as read when the dialog is closed.
            this.notificationService.markAllAsRead();
        });
    }

}
