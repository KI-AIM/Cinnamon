import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NavigationComponent } from './core/components/navigation/navigation.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { StartpageComponent } from './features/start/pages/startpage/startpage.component';
import { UploadFileComponent } from './features/data-upload/pages/upload-file/upload-file.component';
import { TitleService } from './core/services/title-service.service';
import { StateManagementService } from './core/services/state-management.service';

@NgModule({
    declarations: [
        AppComponent,
        NavigationComponent,
        StartpageComponent,
        UploadFileComponent,
    ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        NgbModule
    ],
    providers: [
        TitleService,
        StateManagementService,
    ],
    bootstrap: [AppComponent]
})
export class AppModule { }
