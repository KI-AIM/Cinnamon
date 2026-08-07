import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { NotFoundComponent } from "@core/components/not-found/not-found.component";
import { projectAccessGuard } from "@core/guards/project-access.guard";
import { AdminPageComponent } from "@features/administration/components/admin-page/admin-page.component";
import { ProjectShellComponent } from "@features/project/components/project-shell/project-shell.component";
import { UserHomePageComponent } from "@features/user/pages/user-home-page/user-home-page.component";
import { UserSettingsComponent } from "@features/user/pages/user-settings/user-settings.component";
import { StartpageComponent } from './features/start/pages/startpage/startpage.component';
import { UploadFileComponent } from './features/data-upload/pages/upload-file/upload-file.component';
import { DataConfigurationComponent } from './features/data-upload/pages/data-configuration/data-configuration.component';
import { DataValidationComponent } from './features/data-upload/pages/data-validation/data-validation.component';
import {LoginComponent} from "./features/auth/pages/login/login.component";
import {RegisterComponent} from "./features/auth/pages/register/register.component";
import { AuthGuard } from './core/guards/auth.guard';
import { AnonymizationConfigurationComponent } from './features/anonymization/pages/anonymization-configuration/anonymization-configuration.component';
import {
    SynthetizationConfigurationComponent
} from "./features/synthetization/pages/synthetization-configuration/synthetization-configuration.component";
import { ExecutionComponent } from "./features/execution/pages/execution/execution.component";
import {
    TechnicalEvaluationConfigurationComponent
} from "./features/technical-evaluation/pages/technical-evaluation-configuration/technical-evaluation-configuration.component";
import { EvaluationComponent } from "./features/evaluation/pages/evaluation/evaluation.component";
import {
    RiskAssessmentConfigurationComponent
} from "./features/risk-assessment/pages/risk-assessment-configuration/risk-assessment-configuration.component";
import { ReportComponent } from "./features/report/pages/report/report.component";

const routes: Routes = [
    {path: '', redirectTo: 'login', pathMatch: 'full'},

    {path: 'login', component: LoginComponent},
    {path: 'register', component: RegisterComponent},
    // {path: 'admin', canActivate: [AuthGuard], component: AdminPageComponent},

    {
        path: 'user/-',
        canActivate: [AuthGuard],
        children: [
            {path: 'home', component: UserHomePageComponent},
            {path: 'settings', component: UserSettingsComponent},
        ],
    },
    {
        path: 'project/:projectId',
        component: ProjectShellComponent,
        canActivate: [AuthGuard, projectAccessGuard],
        children: [
            {path: 'start', component: StartpageComponent},
            {path: 'upload', component: UploadFileComponent},
            {path: 'dataConfiguration', component: DataConfigurationComponent},
            {path: 'dataValidation', component: DataValidationComponent},
            {path: 'anonymizationConfiguration', component: AnonymizationConfigurationComponent},
            {path: 'synthetizationConfiguration', component: SynthetizationConfigurationComponent},
            {path: 'execution', component: ExecutionComponent},
            {path: 'technicalEvaluationConfiguration', component: TechnicalEvaluationConfigurationComponent},
            {path: 'riskEvaluationConfiguration', component: RiskAssessmentConfigurationComponent},
            {path: 'evaluation', component: EvaluationComponent},
            {path: 'report', component: ReportComponent},
        ],
    },

    {path: '**', component: NotFoundComponent},
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule {

}
