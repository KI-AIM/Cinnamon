import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from "@angular/forms";
import { MatButton, MatIconButton } from "@angular/material/button";
import { MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from "@angular/material/dialog";
import { MatIcon } from "@angular/material/icon";
import { MatError, MatFormField, MatInput, MatLabel, MatSuffix } from "@angular/material/input";
import {
    MatCell,
    MatCellDef,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatHeaderRow, MatHeaderRowDef, MatNoDataRow, MatRow, MatRowDef,
    MatTable
} from "@angular/material/table";
import { RouterLink } from "@angular/router";
import { SharedModule } from "@shared/shared.module";
import { UserHomePageComponent } from './pages/user-home-page/user-home-page.component';
import { UserSettingsComponent } from './pages/user-settings/user-settings.component';


/**
 * Module for user related pages.
 *
 * @author Daniel Preciado-Marquez
 */
@NgModule({
  declarations: [
    UserHomePageComponent,
    UserSettingsComponent
  ],
    imports: [
        CommonModule,
        MatTable,
        MatColumnDef,
        MatHeaderCell,
        MatHeaderCellDef,
        MatCell,
        MatCellDef,
        MatHeaderRow,
        MatHeaderRowDef,
        MatRow,
        MatRowDef,
        MatIconButton,
        MatIcon,
        MatButton,
        MatDialogActions,
        MatDialogClose,
        MatDialogContent,
        MatDialogTitle,
        MatFormField,
        MatLabel,
        MatError,
        MatInput,
        MatNoDataRow,
        MatSuffix,
        ReactiveFormsModule,
        RouterLink,
        SharedModule,
    ]
})
export class UserModule { }
