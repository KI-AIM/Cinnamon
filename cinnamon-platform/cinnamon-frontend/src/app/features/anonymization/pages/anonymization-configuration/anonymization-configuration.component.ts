import { Component, OnInit } from '@angular/core';
import { Steps } from "@core/enums/steps";
import { TitleService } from "../../../../core/services/title-service.service";
import { AlgorithmService, ConfigurationInfo } from "../../../../shared/services/algorithm.service";
import { AnonymizationService } from "../../services/anonymization.service";
import { AnonymizationAttributeConfigurationComponent } from '../../components/anonymization-attribute-configuration/anonymization-attribute-configuration.component';
import { AdditionalConfig, ConfigurationAdditionalConfigs } from 'src/app/shared/model/configuration-additional-configs';
import { Observable } from "rxjs";

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

    configs = new Array(
        new AdditionalConfig(
            AnonymizationAttributeConfigurationComponent,
            "Attribute Anonymization Configuration",
            "Define anonymization settings for each attribute. Each attribute requires a protection strategy and an interval size if applicable.",
            AnonymizationAttributeConfigurationComponent.formGroupName,
            AnonymizationAttributeConfigurationComponent.initForm,
        )
    )
    additionalConfigs = new ConfigurationAdditionalConfigs(this.configs);

    protected configurationInfo$: Observable<ConfigurationInfo>;

    constructor(
        private readonly anonymizationService: AnonymizationService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Anonymization");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.anonymizationService.fetchInfo();
    }
}
