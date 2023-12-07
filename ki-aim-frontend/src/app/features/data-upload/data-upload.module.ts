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
import { DateformatComponent } from './components/configurationSettings/dateformat/dateformat.component';
import { DatetimeformatComponent } from './components/configurationSettings/datetimeformat/datetimeformat.component';
import { StringpatternComponent } from './components/configurationSettings/stringpattern/stringpattern.component';
import { AddedConfigurationListComponent } from './components/added-configuration-list/added-configuration-list.component';
import { DataTableComponent } from './components/data-table/data-table.component';
import { TransformationService } from './services/transformation.service';
import { MatTableModule } from '@angular/material/table'
import { CdkColumnDef } from '@angular/cdk/table';

@NgModule({
    declarations: [
        AdditionalConfigurationComponent,
        AttributeConfigurationComponent,
        DataConfigurationComponent,
        UploadFileComponent,
        DataValidationComponent,
        DateformatComponent,
        DatetimeformatComponent,
        StringpatternComponent,
        AddedConfigurationListComponent,
        DataTableComponent,
    ],
    imports: [
        CommonModule, 
        FormsModule,
        RouterModule,
        MatTableModule,
    ],
    exports: [
        AdditionalConfigurationComponent,
        AttributeConfigurationComponent,
        DataConfigurationComponent,
        UploadFileComponent,
    ],
    providers: [
        FileService,
        TransformationService,
        CdkColumnDef
    ]
})
export class DataUploadModule {}
