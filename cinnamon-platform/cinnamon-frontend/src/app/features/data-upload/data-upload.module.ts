import { NgModule, inject, provideAppInitializer } from "@angular/core";
import { CommonModule } from "@angular/common";
import { MatTooltip } from "@angular/material/tooltip";
import { AdditionalConfigurationComponent } from "./components/additional-configuration/additional-configuration.component";
import { AttributeConfigurationComponent } from "./components/attribute-configuration/attribute-configuration.component";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { DataConfigurationComponent } from "./pages/data-configuration/data-configuration.component";
import { UploadFileComponent } from "./pages/upload-file/upload-file.component";
import { RouterModule } from "@angular/router";
import { FileService } from "./services/file.service";
import { DataValidationComponent } from "./pages/data-validation/data-validation.component";
import { DateformatComponent } from "./components/configurationSettings/dateformat/dateformat.component";
import { DatetimeformatComponent } from "./components/configurationSettings/datetimeformat/datetimeformat.component";
import { StringpatternComponent } from "./components/configurationSettings/stringpattern/stringpattern.component";
import { AddedConfigurationListComponent } from "./components/added-configuration-list/added-configuration-list.component";
import { MatDialogModule } from "@angular/material/dialog";
import { CdkColumnDef } from "@angular/cdk/table";
import { SharedModule } from "src/app/shared/shared.module";
import { MatButtonModule } from "@angular/material/button";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatIconModule } from "@angular/material/icon";
import { MatSelectModule } from "@angular/material/select";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { RangeComponent } from './components/configurationSettings/range/range.component';
import { DataConfigurationService } from "src/app/shared/services/data-configuration.service";
import { NgxEchartsModule } from "ngx-echarts";

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
        RangeComponent,
	],
    imports: [
        CommonModule,
        FormsModule,
        RouterModule,
        SharedModule,
        MatDialogModule,
        MatButtonModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatInputModule,
        MatIconModule,
        MatSelectModule,
        ReactiveFormsModule,
        MatCheckboxModule,
        NgxEchartsModule,
        MatTooltip,
    ],
	exports: [
		AdditionalConfigurationComponent,
		AttributeConfigurationComponent,
		DataConfigurationComponent,
		UploadFileComponent,
	],
	providers: [
		FileService,
		CdkColumnDef,
		provideAppInitializer(() => {
        const initializerFn = ((service: DataConfigurationService) => function () { return service.registerConfig(); })(inject(DataConfigurationService));
        return initializerFn();
      }),
	],
})
export class DataUploadModule {}
