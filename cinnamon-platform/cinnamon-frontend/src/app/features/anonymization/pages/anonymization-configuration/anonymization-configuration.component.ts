import { Component, OnInit } from '@angular/core';
import { FormGroup } from "@angular/forms";
import { Steps } from "@core/enums/steps";
import {
    AnonymizationAttributeConfigurationService
} from "@features/anonymization/services/anonymization-attribute-configuration.service";
import {
    AnonymizationAttributeRowConfiguration,
    ConfigurationObject
} from "@shared/model/anonymization-attribute-config";
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
import { TextAnonymizationConfigurationComponent } from '../../components/text-anonymization-configuration/text-anonymization-configuration.component';
import { TextAnonymizationConfigurationService } from '../../services/text-anonymization-configuration.service';
import { hasStructuredColumns, hasTextColumns } from '@shared/model/data-configuration';
import { TextAnonymizationType } from '@shared/model/text-anonymization.types';

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
        private readonly textAnonymizationConfigurationService: TextAnonymizationConfigurationService,
    ) {
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.anonymizationService.getInfo();

        const configs = [new AdditionalConfig(
                AnonymizationAttributeConfigurationComponent,
                "Attribute Anonymization Configuration",
                "Define anonymization settings for each attribute. Each attribute requires a protection strategy and an interval size if applicable.",
                this.anonymizationAttributeConfigurationService.formGroupName,
                (form: FormGroup, configs: ConfigurationObject[] | null, disabled: boolean) => {
                    this.anonymizationAttributeConfigurationService.initForm(form, configs as AnonymizationAttributeRowConfiguration[] | null, disabled);
                },
                null,
                null,
                (_, dataConfiguration) => hasStructuredColumns(dataConfiguration),
                'anonymization',
            ),
            new AdditionalConfig(
                TextAnonymizationConfigurationComponent,
                'PII Text Anonymization',
                'Configure how personally identifiable information (PII) in free-text columns is detected and transformed.',
                this.textAnonymizationConfigurationService.piiFormGroupName,
                (form: FormGroup, config: any, disabled: boolean) => {
                    this.textAnonymizationConfigurationService.initForm(
                        form,
                        config,
                        disabled,
                        this.textAnonymizationConfigurationService.piiFormGroupName,
                        true,
                    );
                },
                null,
                null,
                (_, dataConfiguration) => hasTextColumns(dataConfiguration),
                'text_anonymization',
                {
                    configurationGroupName: this.textAnonymizationConfigurationService.piiFormGroupName,
                    textAnonymizationType: TextAnonymizationType.PII,
                    defaultEnabled: true,
                },
            ),
            new AdditionalConfig(
                TextAnonymizationConfigurationComponent,
                'Sensitive Data Text Anonymization',
                'Configure how sensitive clinical information such as medication, treatment, and diagnosis is detected and transformed.',
                this.textAnonymizationConfigurationService.sensitiveFormGroupName,
                (form: FormGroup, config: any, disabled: boolean) => {
                    this.textAnonymizationConfigurationService.initForm(
                        form,
                        config,
                        disabled,
                        this.textAnonymizationConfigurationService.sensitiveFormGroupName,
                        false,
                    );
                },
                null,
                null,
                (_, dataConfiguration) => hasTextColumns(dataConfiguration),
                'text_anonymization',
                {
                    configurationGroupName: this.textAnonymizationConfigurationService.sensitiveFormGroupName,
                    textAnonymizationType: TextAnonymizationType.SENSITIVE,
                    defaultEnabled: false,
                },
            )];
        this.additionalConfigs = new ConfigurationAdditionalConfigs(configs);
    }
}
