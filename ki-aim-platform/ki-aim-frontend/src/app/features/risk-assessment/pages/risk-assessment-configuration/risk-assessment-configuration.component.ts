import { Component } from '@angular/core';
import { Steps } from "../../../../core/enums/steps";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../../../environments/environment";
import { Router } from "@angular/router";
import { StatusService } from "../../../../shared/services/status.service";

@Component({
  selector: 'app-risk-assessment-configuration',
  templateUrl: './risk-assessment-configuration.component.html',
  styleUrls: ['./risk-assessment-configuration.component.less']
})
export class RiskAssessmentConfigurationComponent {

    private baseUrl: string = environments.apiUrl + "/api/process";

    constructor(
        private readonly httpClient: HttpClient,
        private readonly router: Router,
        private readonly statusService: StatusService,
    ) {
    }

    protected configure(): void {
        const formData = new FormData();
        formData.append("configuration", config);
        formData.append("stepName", "RISK_EVALUATION");
        formData.append("url", "null");

        this.httpClient.post<void>(this.baseUrl + "/evaluation/configure", formData).subscribe({
            next: () => {
                this.router.navigateByUrl("/evaluation");
                this.statusService.setNextStep(Steps.EVALUATION);
            },
            error: err => {
                // this.error = `Failed to save configuration. Status: ${err.status} (${err.statusText})`;
            }
        });
    }

}

const config = "risk_assessment_configuration:\n" +
    "  data_format: \"cross-sectional\"\n" +
    "  train_fraction: 0.8\n" +
    "  targets: []\n" +
    "  n_random_targets: 5\n" +
    "  n_outlier_targets: 5\n" +
    "  n_iterations: 1\n" +
    "  columns_excluded: [\"id\"]\n" +
    "  linkage:\n" +
    "    n_attacks: 100\n" +
    "    available_columns: [\"ChestPainType\",\"RestingBP\",\"Cholesterol\",\"FastingBS\",\"RestingECG\",\"MaxHR\",\"ExerciseAngina\",\n" +
    "                   \"Oldpeak\",\"ST_Slope\",\"HeartDisease\"]\n" +
    "    unavailable_columns: [\"birthdate\",\"death_date\",\"Age\",\"Sex\",]  # unavailable and available sets of columns e.g. lab data and demographic data\n" +
    "  singlingout-uni:\n" +
    "    n_attacks: 100\n" +
    "  singlingout-multi:\n" +
    "    n_attacks: 100\n" +
    "  attribute_inference:\n" +
    "    n_attacks: 100\n" +
    "  metrics:\n" +
    "    uniqueness: true";
