import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RiskAssessmentConfigurationComponent } from './pages/risk-assessment-configuration/risk-assessment-configuration.component';
import { SharedModule } from 'src/app/shared/shared.module';
import { RiskAssessmentService } from './services/risk-assessment.service';



@NgModule({
  declarations: [
      RiskAssessmentConfigurationComponent
  ],
  imports: [
      CommonModule,
      SharedModule
  ],
  providers: [
      {
          // Calls the useFactory function when starting the application
          provide: APP_INITIALIZER,
          useFactory: (service: RiskAssessmentService) => function () {
              return service.registerConfig();
          },
          deps: [RiskAssessmentService],
          multi: true,
      },
  ],
})
export class RiskAssessmentModule { }
