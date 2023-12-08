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
import { LoginComponent } from './core/components/login/login.component';
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatButtonModule } from "@angular/material/button";
import { RegisterComponent } from './core/components/register/register.component';
import { MatCardModule } from "@angular/material/card";
import { UserService } from './shared/services/user.service';
import { XhrInterceptor } from './core/interceptor/xhr.interceptor';

@NgModule({
    declarations: [
        AppComponent,
        NavigationComponent,
        LoginComponent,
        RegisterComponent,
    ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        StartModule,
        DataUploadModule,
        HttpClientModule,
        FormsModule,
        NgbModule,
        BrowserAnimationsModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        ReactiveFormsModule,
        MatCardModule,
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
