import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NavigationComponent } from './core/components/navigation/navigation.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TitleService } from './core/services/title-service.service';
import { StateManagementService } from './core/services/state-management.service';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { DataService } from './shared/services/data.service';
import { DataConfigurationService } from './shared/services/data-configuration.service';
import { DataUploadModule } from './features/data-upload/data-upload.module';
import { StartModule } from './features/start/start.module';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatButtonModule } from "@angular/material/button";
import { UserService } from './shared/services/user.service';
import { XhrInterceptor } from './core/interceptor/xhr.interceptor';
import { MatIconModule } from '@angular/material/icon';
import { SharedModule } from './shared/shared.module';
import { AuthModule } from './features/auth/auth.module';
import { MatDialogModule } from "@angular/material/dialog";
import { MatOptionModule } from "@angular/material/core";
import { MatSelectModule } from "@angular/material/select";
import { MatCheckboxModule } from '@angular/material/checkbox';
import { AnonymizationModule } from "./features/anonymization/anonymization.module";
import { SynthetizationModule } from "./features/synthetization/synthetization.module";
import { ExecutionModule } from "./features/execution/execution.module";
import { TechnicalEvaluationModule } from "./features/technical-evaluation/technical-evaluation.module";
import { EvaluationModule } from "./features/evaluation/evaluation.module";
import { RiskAssessmentModule } from "./features/risk-assessment/risk-assessment.module";
import { NotFoundComponent } from './core/components/not-found/not-found.component';

@NgModule({
    declarations: [
        AppComponent,
        NavigationComponent,
        NotFoundComponent,
    ],
    imports: [
        BrowserModule,
        SharedModule,
        AnonymizationModule,
        AppRoutingModule,
        AuthModule,
        EvaluationModule,
        ExecutionModule,
        StartModule,
        SynthetizationModule,
        TechnicalEvaluationModule,
        RiskAssessmentModule,
        DataUploadModule,
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
        { provide: HTTP_INTERCEPTORS, useClass: XhrInterceptor, multi: true },
        provideHttpClient(withInterceptorsFromDi()),
    ],
    bootstrap: [AppComponent]
})
export class AppModule { }
