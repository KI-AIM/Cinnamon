import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdditionalConfigurationComponent } from './components/additional-configuration/additional-configuration.component';
import { AttributeConfigurationComponent } from './components/attribute-configuration/attribute-configuration.component';
import { FormsModule } from '@angular/forms';
import { DataConfigurationComponent } from './pages/data-configuration/data-configuration.component';
import { UploadFileComponent } from './pages/upload-file/upload-file.component';
import { RouterModule } from '@angular/router';
import { FileService } from './services/file.service';
import { DataValidationComponent } from './pages/data-validation/data-validation.component';

@NgModule({
    declarations: [
        AdditionalConfigurationComponent,
        AttributeConfigurationComponent,
        DataConfigurationComponent,
        UploadFileComponent,
        DataValidationComponent,
    ],
    imports: [
        CommonModule, 
        FormsModule,
        RouterModule,
    ],
    exports: [
        AdditionalConfigurationComponent,
        AttributeConfigurationComponent,
        DataConfigurationComponent,
        UploadFileComponent,
    ],
    providers: [
        FileService,
    ]
})
export class DataUploadModule {}
