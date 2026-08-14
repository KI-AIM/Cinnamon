import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from "@angular/forms";
import { MatButton, MatIconButton } from "@angular/material/button";
import { MatCheckbox } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIcon } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatPaginator } from "@angular/material/paginator";
import { MatProgressSpinner } from "@angular/material/progress-spinner";
import { MatSelectModule } from "@angular/material/select";
import { MatSortModule } from "@angular/material/sort";
import { MatTabsModule } from "@angular/material/tabs";
import {
    MatCell,
    MatCellDef,
    MatColumnDef, MatHeaderCell, MatHeaderCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatRow,
    MatRowDef,
    MatTable
} from "@angular/material/table";
import { RouterModule } from "@angular/router";
import { SharedModule } from "@shared/shared.module";
import {
    UserInvitationFormComponent
} from "@features/administration/components/user-invitation-form/user-invitation-form.component";
import {
    UserInvitationsComponent
} from "@features/administration/components/user-invitations/user-invitations.component";
import { AdminMailSettingsComponent } from './components/admin-mail-settings/admin-mail-settings.component';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { AdminUsersComponent } from './components/admin-users/admin-users.component';
import { MailTemplatesComponent } from './components/mail-templates/mail-templates.component';


@NgModule({
    declarations: [
        AdminShellComponent,
        AdminUsersComponent,
        AdminMailSettingsComponent,
        MailTemplatesComponent,
        UserInvitationsComponent,
        UserInvitationFormComponent
    ],
    imports: [
        CommonModule,
        MatDialogModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatIcon,
        MatCheckbox,
        MatTable,
        MatColumnDef,
        MatCellDef,
        MatRow,
        MatRowDef,
        MatHeaderRow,
        MatHeaderRowDef,
        MatHeaderCell,
        MatCell,
        MatHeaderCellDef,
        MatIconButton,
        MatButton,
        MatSortModule,
        MatTabsModule,
        MatPaginator,
        MatProgressSpinner,
        ReactiveFormsModule,
        RouterModule,
        SharedModule,
    ]
})
export class AdministrationModule {
}
