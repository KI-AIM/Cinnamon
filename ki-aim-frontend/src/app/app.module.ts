import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NavigationComponent } from './core/components/navigation/navigation.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TitleService } from './core/services/title-service.service';
import { StateManagementService } from './core/services/state-management.service';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { DataService } from './shared/services/data.service';
import { DataConfigurationService } from './shared/services/data-configuration.service';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { DataUploadModule } from './features/data-upload/data-upload.module';
import { StartModule } from './features/start/start.module';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { UserService } from './shared/services/user.service';
import { XhrInterceptor } from './core/interceptor/xhr.interceptor';
import { MatIconModule } from '@angular/material/icon';
import { SharedModule } from './shared/shared.module';
import { AuthModule } from './features/auth/auth.module';
import { AnonymizationConfigurationComponent } from './features/anonymization/anonymization-configuration/anonymization-configuration.component';
import { ConfigurationManagementComponent } from './features/configuration/components/configuration-management/configuration-management.component';
import { MatDialogModule } from "@angular/material/dialog";
import { MatOptionModule } from "@angular/material/core";
import { MatSelectModule } from "@angular/material/select";
import { MatCheckboxModule } from '@angular/material/checkbox';

@NgModule({
    declarations: [
        AppComponent,
        NavigationComponent,
        AnonymizationConfigurationComponent,
        ConfigurationManagementComponent,
    ],
    imports: [
        BrowserModule,
        SharedModule,
        AppRoutingModule,
        AuthModule,
        StartModule,
        DataUploadModule,
        HttpClientModule,
        NgbModule,
        BrowserAnimationsModule,
        MatIconModule,
        MatButtonModule,
        MatCheckboxModule,
        MatDialogModule,
        MatFormFieldModule,
        MatOptionModule,
        MatSelectModule,
    ],
    providers: [
        TitleService,
        StateManagementService,
        UserService,
        DataService,
        DataConfigurationService,
        {provide: HTTP_INTERCEPTORS, useClass: XhrInterceptor, multi: true},
    ],
    bootstrap: [AppComponent]
})
export class AppModule { }
