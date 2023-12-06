import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NavigationComponent } from './core/components/navigation/navigation.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TitleService } from './core/services/title-service.service';
import { StateManagementService } from './core/services/state-management.service';
import { HttpClientModule } from '@angular/common/http';
import { DataService } from './shared/services/data.service';
import { DataConfigurationService } from './shared/services/data-configuration.service';
import { FormsModule } from '@angular/forms';
import { DataUploadModule } from './features/data-upload/data-upload.module';
import { StartModule } from './features/start/start.module';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

@NgModule({
    declarations: [
        AppComponent,
        NavigationComponent,
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
    ],
    providers: [
        TitleService,
        StateManagementService,
        DataService,
        DataConfigurationService,
    ],
    bootstrap: [AppComponent]
})
export class AppModule { }
