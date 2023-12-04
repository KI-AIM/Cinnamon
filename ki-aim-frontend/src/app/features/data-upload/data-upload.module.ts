import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdditionalConfigurationComponent } from './components/additional-configuration/additional-configuration.component';
import { AttributeConfigurationComponent } from './components/attribute-configuration/attribute-configuration.component';
import { FormsModule } from '@angular/forms';
import { DataConfigurationComponent } from './pages/data-configuration/data-configuration.component';
import { UploadFileComponent } from './pages/upload-file/upload-file.component';
import { RouterModule } from '@angular/router';

@NgModule({
    declarations: [
        AdditionalConfigurationComponent,
        AttributeConfigurationComponent,
        DataConfigurationComponent,
        UploadFileComponent,
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
})
export class DataUploadModule {}
