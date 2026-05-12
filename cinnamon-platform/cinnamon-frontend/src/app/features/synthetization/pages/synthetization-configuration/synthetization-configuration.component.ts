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
import {
    supportsFreeTextData,
    supportsStructuredData
} from "../../../../shared/model/algorithm";
import { FormGroup } from "@angular/forms";
import { ConfigurationObject } from "../../../../shared/model/anonymization-attribute-config";
import {
    TextSynthesisConfigurationService
} from "../../services/text-synthesis-configuration.service";
import {
    TextSynthesisConfigurationComponent
} from "../../components/text-synthesis-configuration/text-synthesis-configuration.component";
import { hasTextColumns } from "../../../../shared/model/data-configuration";

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
        private readonly textSynthesisConfigurationService: TextSynthesisConfigurationService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Synthetization");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.synthService.fetchInfo();

        const configs = new Array(
            new AdditionalConfig(
                TextSynthesisConfigurationComponent,
                "Free-Text Synthesizer",
                "Choose and configure the free-text synthesizer that enriches TEXT columns after structured synthesis.",
                this.textSynthesisConfigurationService.formGroupName,
                (form: FormGroup, config: any, disabled: boolean) => {
                    this.textSynthesisConfigurationService.initForm(form, config as ConfigurationObject | null, disabled);
                },
                null,
                "__external_step__",
                (algorithm, dataConfiguration) => {
                    return hasTextColumns(dataConfiguration)
                        && supportsStructuredData(algorithm)
                        && !supportsFreeTextData(algorithm);
                },
            ),
        );
        this.additionalConfigs = new ConfigurationAdditionalConfigs(configs);
    }
}
