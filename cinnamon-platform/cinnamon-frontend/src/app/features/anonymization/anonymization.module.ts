import { NgModule, inject, provideAppInitializer } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AnonymizationConfigurationComponent } from './pages/anonymization-configuration/anonymization-configuration.component';
import { SharedModule } from '../../shared/shared.module';
import { AnonymizationService } from './services/anonymization.service';
import { DataUploadModule } from '../data-upload/data-upload.module';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AnonymizationAttributeConfigurationComponent } from './components/anonymization-attribute-configuration/anonymization-attribute-configuration.component';
import { AnonymizationAttributeRowComponent } from './components/anonymization-attribute-row/anonymization-attribute-row.component';
import { AnonymizationAttributeConfigurationDirective } from './directives/anonymization-attribute-configuration.directive';

@NgModule({
    declarations: [
        AnonymizationConfigurationComponent,
        AnonymizationAttributeConfigurationComponent,
        AnonymizationAttributeRowComponent,
        AnonymizationAttributeConfigurationDirective,
    ],
    imports: [
        CommonModule,
        SharedModule,
        FormsModule,
        DataUploadModule,
        MatTableModule,
        MatPaginatorModule,
        MatDialogModule,
        MatButtonModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatInputModule,
        MatIconModule,
        MatSelectModule,
        ReactiveFormsModule
    ],
    providers: [
        provideAppInitializer(() => {
        const initializerFn = ((service: AnonymizationService) =>
                function () {
                    return service.registerConfig();
                })(inject(AnonymizationService));
        return initializerFn();
      }),
    ],
})
export class AnonymizationModule {}
