import { Component, OnInit } from '@angular/core';
import { FormGroup } from "@angular/forms";
import { Steps } from "@core/enums/steps";
import { TitleService } from "@core/services/title-service.service";
import {
    AnonymizationAttributeConfigurationService
} from "@features/anonymization/services/anonymization-attribute-configuration.service";
import { AnonymizationAttributeRowConfiguration } from "@shared/model/anonymization-attribute-config";
import { AlgorithmService, ConfigurationInfo } from "@shared/services/algorithm.service";
import { Observable } from "rxjs";
import {
    AdditionalConfig,
    ConfigurationAdditionalConfigs
} from 'src/app/shared/model/configuration-additional-configs';
import {
    AnonymizationAttributeConfigurationComponent
} from '../../components/anonymization-attribute-configuration/anonymization-attribute-configuration.component';
import { AnonymizationService } from "../../services/anonymization.service";

@Component({
    selector: 'app-anonymization-configuration',
    templateUrl: './anonymization-configuration.component.html',
    styleUrls: ['./anonymization-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: AnonymizationService
        },
    ],
    standalone: false
})
export class AnonymizationConfigurationComponent implements OnInit {
    protected readonly Steps = Steps;

    protected additionalConfigs: ConfigurationAdditionalConfigs;

    protected configurationInfo$: Observable<ConfigurationInfo>;

    constructor(
        private readonly anonymizationService: AnonymizationService,
        private readonly anonymizationAttributeConfigurationService: AnonymizationAttributeConfigurationService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Anonymization");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.anonymizationService.fetchInfo();

        const configs = new Array(
            new AdditionalConfig(
                AnonymizationAttributeConfigurationComponent,
                "Attribute Anonymization Configuration",
                "Define anonymization settings for each attribute. Each attribute requires a protection strategy and an interval size if applicable.",
                this.anonymizationAttributeConfigurationService.formGroupName,
                (form: FormGroup, configs: AnonymizationAttributeRowConfiguration[] | null, disabled: boolean) => this.anonymizationAttributeConfigurationService.initForm(form, configs, disabled),
            ),
        );
        this.additionalConfigs = new ConfigurationAdditionalConfigs(configs);
    }
}
