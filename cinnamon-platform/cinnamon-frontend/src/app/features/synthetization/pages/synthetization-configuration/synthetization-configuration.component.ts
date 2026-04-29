import { Component, OnInit } from '@angular/core';
import { Steps } from "@core/enums/steps";
import { TitleService } from "../../../../core/services/title-service.service";
import { AlgorithmService, ConfigurationInfo } from "../../../../shared/services/algorithm.service";
import { SynthetizationService } from "../../services/synthetization.service";
import { Observable } from "rxjs";
import {
    AdditionalConfig,
    ConfigurationAdditionalConfigs
} from "../../../../shared/model/configuration-additional-configs";
import { FormGroup } from "@angular/forms";
import {
    LlmRedactionRulesConfigurationComponent
} from "../../components/llm-redaction-rules-configuration/llm-redaction-rules-configuration.component";
import {
    LlmRedactionRuleConfiguration,
    LlmRedactionRulesConfigurationService
} from "../../services/llm-redaction-rules-configuration.service";
import { ConfigurationObject } from "../../../../shared/model/anonymization-attribute-config";

@Component({
    selector: 'app-synthetization-configuration',
    templateUrl: './synthetization-configuration.component.html',
    styleUrls: ['./synthetization-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: SynthetizationService
        },
    ],
    standalone: false
})
export class SynthetizationConfigurationComponent implements OnInit {
    protected readonly Steps = Steps;

    protected additionalConfigs: ConfigurationAdditionalConfigs;
    protected configurationInfo$: Observable<ConfigurationInfo>;

    constructor(
        private readonly synthService: SynthetizationService,
        private readonly llmRedactionRulesConfigurationService: LlmRedactionRulesConfigurationService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Synthetization");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.synthService.fetchInfo();

        const configs = new Array(
            new AdditionalConfig(
                LlmRedactionRulesConfigurationComponent,
                "Redaction Rules",
                "Optional semantic rules for LLM-based de-identification. Example: Age -> [AGE]. These rules guide the model but are not hard guarantees.",
                this.llmRedactionRulesConfigurationService.formGroupName,
                (form: FormGroup, configs: ConfigurationObject[] | null, disabled: boolean) => {
                    this.llmRedactionRulesConfigurationService.initForm(form, configs as LlmRedactionRuleConfiguration[] | null, disabled);
                },
                ["llm_text_redaction"],
                "model_fitting",
            ),
        );
        this.additionalConfigs = new ConfigurationAdditionalConfigs(configs);
    }
}
