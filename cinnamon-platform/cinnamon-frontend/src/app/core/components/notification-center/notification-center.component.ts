import { Component, OnInit, TemplateRef } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { FileType } from "@shared/model/file-configuration";
import { Observable } from "rxjs";

@Component({
  selector: 'app-notification-center',
  standalone: false,
  templateUrl: './notification-center.component.html',
  styleUrl: './notification-center.component.less'
})
export class NotificationCenterComponent implements OnInit {

    protected notifications$: Observable<AppNotification[]>;

    public constructor(
        protected readonly matDialog: MatDialog,
        private readonly notificationService: NotificationService,
    ) { }

    public ngOnInit(): void {
        this.notifications$ = this.notificationService.notifications$();
    }

    protected openCenter(dialog: TemplateRef<MatDialog>, trigger: HTMLAnchorElement) {
        const rect = trigger.getBoundingClientRect();
        this.matDialog.open(dialog, {
            width: '300px',
            disableClose: false,
            hasBackdrop: true,
            position: {
                top: `${rect.bottom}px`,
                right: `${window.innerWidth - rect.left}px`,
            }
        });
    }

}
