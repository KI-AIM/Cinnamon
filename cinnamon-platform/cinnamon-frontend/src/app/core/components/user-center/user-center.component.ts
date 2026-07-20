import {Component, OnInit, TemplateRef} from '@angular/core';
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {UserService} from "@shared/services/user.service";
import {Observable} from "rxjs";
import {User} from "@shared/model/user";

@Component({
  selector: 'app-user-center',
  standalone: false,
  templateUrl: './user-center.component.html',
  styleUrl: './user-center.component.less'
})
export class UserCenterComponent implements OnInit {

    private dialog: MatDialogRef<MatDialog, TemplateRef<MatDialog>> | null = null;

    protected user$: Observable<User>;

    constructor(
        private readonly matDialog: MatDialog,
        private readonly userService: UserService,
    ) { }

    public ngOnInit(): void {
        this.user$ = this.userService.user$;
    }

    protected openCenter(dialog: TemplateRef<MatDialog>, trigger: HTMLAnchorElement): void {
        if (this.dialog) {
            return;
        }

        const rect = trigger.getBoundingClientRect();

        this.dialog = this.matDialog.open(dialog, {
            width: '300px',
            autoFocus: false,
            disableClose: false,
            hasBackdrop: true,
            position: {
                top: `${rect.bottom}px`,
                right: `${window.innerWidth - rect.left}px`,
            }
        });

        this.dialog.afterClosed().subscribe(() => {
            this.dialog = null;
        });
    }

    protected closeCenter(): void {
        if (this.dialog) {
            this.dialog.close();
        }
    }

    protected logout(): void {
        this.userService.logout("close");
        if (this.dialog) {
            this.dialog.close();
        }
    }

}
