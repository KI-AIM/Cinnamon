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
import { SharedModule } from "@shared/shared.module";
import { AdminPageComponent } from './components/admin-page/admin-page.component';
import { MailTemplatesComponent } from './components/mail-templates/mail-templates.component';



@NgModule({
  declarations: [
    AdminPageComponent,
    MailTemplatesComponent
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
        SharedModule,
    ]
})
export class AdministrationModule { }
