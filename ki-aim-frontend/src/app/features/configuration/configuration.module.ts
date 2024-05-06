import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { ConfigurationManagementComponent } from './components/configuration-management/configuration-management.component';
import { ConfigurationUploadComponent } from './components/configuration-upload/configuration-upload.component';
import { SharedModule } from 'src/app/shared/shared.module';

@NgModule({
  declarations: [
    ConfigurationManagementComponent,
    ConfigurationUploadComponent,
  ],
  imports: [
    CommonModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatIconModule,
    SharedModule,
  ],
  exports: [
    ConfigurationManagementComponent,
    ConfigurationUploadComponent,
  ]
})
export class ConfigurationModule { }
