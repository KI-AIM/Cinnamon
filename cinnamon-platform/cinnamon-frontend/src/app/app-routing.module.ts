import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
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
import { StateGuard } from "./core/guards/state.guard";
import { ExecutionComponent } from "./features/execution/pages/execution/execution.component";
import {
    TechnicalEvaluationConfigurationComponent
} from "./features/technical-evaluation/pages/technical-evaluation-configuration/technical-evaluation-configuration.component";
import { EvaluationComponent } from "./features/evaluation/pages/evaluation/evaluation.component";
import {
    RiskAssessmentConfigurationComponent
} from "./features/risk-assessment/pages/risk-assessment-configuration/risk-assessment-configuration.component";

const routes: Routes = [
    {path: '', redirectTo: '', pathMatch: 'full', canActivate: [AuthGuard, StateGuard]},
    {path: 'login' , component: LoginComponent},
    {path: 'register', component: RegisterComponent},
    {path: 'start', component: StartpageComponent, canActivate: [AuthGuard]},
    {path: 'upload', component: UploadFileComponent, canActivate: [AuthGuard]},
    {path: 'dataConfiguration', component: DataConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'dataValidation', component: DataValidationComponent, canActivate: [AuthGuard]},
    {path: 'anonymizationConfiguration', component: AnonymizationConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'synthetizationConfiguration', component: SynthetizationConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'execution', component: ExecutionComponent, canActivate: [AuthGuard]},
    {path: 'technicalEvaluationConfiguration', component: TechnicalEvaluationConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'riskEvaluationConfiguration', component: RiskAssessmentConfigurationComponent, canActivate: [AuthGuard]},
    {path: 'evaluation', component: EvaluationComponent, canActivate: [AuthGuard]},
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule {

}
