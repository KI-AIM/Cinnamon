import {
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    OnInit,
    Output,
    QueryList,
    ViewChild,
    ViewChildren
} from '@angular/core';
import { FormArray, FormControl, FormGroup, ValidatorFn, Validators } from "@angular/forms";
import { ConfigurationObject } from "@shared/model/anonymization-attribute-config";
import { DataConfiguration } from "@shared/model/data-configuration";
import { catchError, Observable, of, tap } from "rxjs";
import { ConfigurationInputType } from "../../model/configuration-input-type";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import { AlgorithmService, ConfigData } from "../../services/algorithm.service";
import { Algorithm } from "../../model/algorithm";
import {
    ConfigurationGroupDefinition,
} from "../../model/configuration-group-definition";
import { ConfigurationGroupComponent } from "../configuration-group/configuration-group.component";
import { ConfigurationAdditionalConfigs } from '../../model/configuration-additional-configs';
import { ErrorHandlingService } from "../../services/error-handling.service";
import { ConfigurationService } from "../../services/configuration.service";

/**
 * HTML form and submit button for one algorithm.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-configuration-form',
    templateUrl: './configuration-form.component.html',
    styleUrls: ['./configuration-form.component.less'],
    standalone: false
})
export class ConfigurationFormComponent implements OnInit {

    @Input() additionalConfigs: ConfigurationAdditionalConfigs | null = null

    /**
     * The algorithm that is configured with this form.
     */
    @Input() public algorithm!: Algorithm;

    /**
     * The data configuration of the project.
     */
    @Input() public dataConfiguration!: DataConfiguration;

    /**
     * If this form is disabled.
     */
    @Input() public disabled!: boolean;

    /**
     * The initial configuration to be displayed.
     */
    @Input() public initialConfigurationData!: ConfigData;

    /**
     * The definition of the configuration fetched from the external API.
     * @protected
     */
    protected algorithmDefinition: AlgorithmDefinition | null = null;
    protected applicableAdditionalConfigs: ConfigurationAdditionalConfigs | null = null;
    /**
     * Dynamically created form for the configuration.
     * @protected
     */
    public form: FormGroup;

    /**
     * Observable that loads the configurations and creates the form group.
     * @protected
     */
    protected configurationData$: Observable<any>;

    /**
     * Event that gets triggered on every change.
     */
    @Output() public onChange: EventEmitter<boolean> = new EventEmitter();

    @ViewChildren(ConfigurationGroupComponent) private groups: QueryList<ConfigurationGroupComponent>;

    @ViewChild(ConfigurationGroupComponent) private rootGroup: ConfigurationGroupComponent;

    constructor(
        private readonly anonService: AlgorithmService,
        private readonly changeD: ChangeDetectorRef,
        private readonly configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
    ) {
        this.form = new FormGroup({});
    }

    ngOnInit() {
        this.configurationData$ = this.anonService.getAlgorithmDefinition(this.algorithm).pipe(
            tap(value => {
                this.algorithmDefinition = value
            }),
            tap(value => {
                this.applicableAdditionalConfigs = this.resolveAdditionalConfigs(this.additionalConfigs);
                this.form = this.createForm(value, this.initialConfigurationData.config, this.dataConfiguration);

                this.fixAttributeLists(value, this.initialConfigurationData.config, this.form, this.dataConfiguration);
                setTimeout(() => {
                    // Has to be run after the page is initialized
                    for (const group of this.groups) {
                        group.handleMissingOptions(this.initialConfigurationData.config);
                    }
                });

                this.form.valueChanges.pipe().subscribe(_ => {
                    this.onChange.emit(this.valid);
                });

                this.onChange.emit(this.valid);
            }),
            catchError(err => {
                this.errorHandlingService.addError(err, "Failed to load the configuration page. You can skip this step for now or try again later.");
                return of(null);
            }),
        );
    }

    /**
     * Returns if the form and all its inputs are valid.
     */
    public get valid(): boolean {
        return !this.form.invalid;
    }

    /**
     * Gets the raw JSON of the form.
     * Removes option groups that are not selected.
     */
    public get formData(): ConfigurationObject {
        return this.removeUncheckedGroups(this.form.getRawValue());
    }

    /**
     * Reads the configuration from the cache.
     */
    public readFromCache(): void {
        const a = this.configurationService.getConfiguration(this.anonService.getConfigurationName(), this.algorithm)
        if (a) {
            this.doSetConfiguration(this.algorithmDefinition!, this.form, a);
        }
    }

    private doSetConfiguration(algorithmDefinition: AlgorithmDefinition, form: FormGroup, configuration: Object) {
        if (Object.keys(configuration).length === 0) {
            return;
        }

        this.form.patchValue(configuration);
        this.fixAttributeLists(algorithmDefinition, configuration, form, this.dataConfiguration);
        this.form.updateValueAndValidity();
        setTimeout(() => {
            // Has to be run after the page is initialized
            for (const group of this.groups) {
                group.handleMissingOptions(configuration);
            }
            this.rootGroup.patchComponents(configuration);
            this.changeD.detectChanges();
        });
    }

    /**
     * Sets the values for attribute list parameters.
     * These cannot be set via patchValue because of the FormArray.
     *
     * @param cde The definition.
     * @param obj The configuration object. Null if the configuration is not available because of an unchecked option.
     * @param form The form object.
     * @param dataConfiguration The data configuration used for the inverted list.
     * @private
     */
    private fixAttributeLists(cde: ConfigurationGroupDefinition, obj: Record<string, any> | null, form: FormGroup, dataConfiguration: DataConfiguration) {
        if (cde.options) {
            for (const [key, value] of Object.entries(cde.options)) {
                if (obj && obj[key]) {
                    this.fixAttributeLists(value, obj[key], form.controls[key] as FormGroup, dataConfiguration);
                } else {
                    // The configuration is not available if the option is not checked
                    this.fixAttributeLists(value, null, form.controls[key] as FormGroup, dataConfiguration);
                }
            }
        }
        if (cde.configurations) {
            for (const [key, value] of Object.entries(cde.configurations)) {
                if (obj && obj[key]) {
                    this.fixAttributeLists(value, obj[key] as Object, form.controls[key] as FormGroup, dataConfiguration);
                }
            }
        }
        if (cde.parameters) {

            for (const key of cde.parameters) {
                if (key.type === ConfigurationInputType.ATTRIBUTE_LIST) {

                    const list = obj == null ? [] : obj[key.name];
                    const formSelected = (form.get(key.name) as FormArray);
                    formSelected.clear();
                    for (const item of list) {
                        formSelected.push(new FormControl(item));
                    }

                    if (key.invert) {
                        const formInverted = (form.get(key.invert) as FormArray);
                        formInverted.clear();

                        for (const attribute of dataConfiguration.configurations) {
                            if (list.includes(attribute.name)) {
                                continue;
                            }
                            formInverted.push(new FormControl(attribute.name));
                        }
                    }
                } else if (key.type === ConfigurationInputType.NAMED_LIST) {
                    const list = Array.isArray(obj?.[key.name]) ? obj[key.name] : [];
                    const formList = form.get(key.name) as FormArray;
                    formList.clear();
                    for (const item of list) {
                        formList.push(this.createNamedListItemGroup(item, this.disabled));
                    }
                }
            }
        }
    }

    /**
     * Dynamically creates the form object based on the given definition.
     * HTML must be created separately inside the HTML file as well.
     *
     * @param algorithmDefinition The definition.
     * @param initialValues Initial values.
     * @param dataConfig Data configuration for initializing attribute lists.
     * @private
     */
    private createForm(algorithmDefinition: AlgorithmDefinition, initialValues: any, dataConfig: DataConfiguration): FormGroup {
        const form = this.createGroup(algorithmDefinition, initialValues, dataConfig);

        if (this.applicableAdditionalConfigs) {
            for (const additionalConfig of this.applicableAdditionalConfigs.configs) {
                const config = initialValues[additionalConfig.formGroupName] ? initialValues[additionalConfig.formGroupName] : null;
                additionalConfig.initializeForm(form, config, this.disabled);
            }
        }

        return form;
    }

    private createGroups(formGroup: any, configurations: {
        [name: string]: ConfigurationGroupDefinition
    }, initialValues: any, dataConfig: DataConfiguration) {
        Object.entries(configurations).forEach(([name, groupDefinition]) => {
            formGroup[name] = this.createGroup(groupDefinition, initialValues[name] ?? {}, dataConfig);
        });
    }

    private createGroup(groupDefinition: ConfigurationGroupDefinition, initialValues: any, dataConfig: DataConfiguration): FormGroup {
        const group: any = {};

        if (groupDefinition.parameters) {
            groupDefinition.parameters.forEach(inputDefinition => {
                const mandatory = this.isMandatory(inputDefinition);
                if (inputDefinition.type === ConfigurationInputType.LIST) {
                    const initialValue = initialValues[inputDefinition.name] ?? inputDefinition.default_value;

                    const controls = [];
                    for (const defaultValue of initialValue as number[]) {
                        controls.push(new FormControl({
                            value: defaultValue,
                            disabled: this.disabled
                        }, Validators.required));
                    }

                    group[inputDefinition.name] = new FormArray(controls, mandatory ? Validators.required : null);
                } else if (inputDefinition.type === ConfigurationInputType.NAMED_LIST) {
                    const initialValue = initialValues[inputDefinition.name] ?? inputDefinition.default_value ?? [];
                    const controls = [];
                    for (const item of initialValue as Array<{name?: string, description?: string}>) {
                        controls.push(this.createNamedListItemGroup(item, this.disabled));
                    }
                    group[inputDefinition.name] = new FormArray(controls, mandatory ? Validators.required : null);
                } else if (inputDefinition.type === ConfigurationInputType.ATTRIBUTE_LIST) {
                    group[inputDefinition.name] = new FormArray([], mandatory ? Validators.required : null);
                    if (inputDefinition.invert) {
                        group[inputDefinition.invert] = new FormArray([], mandatory ? Validators.required : null);
                        for (const attribute of dataConfig.configurations) {
                            group[inputDefinition.invert].push(new FormControl(attribute.name));
                        }
                    }
                } else {
                    // Add validators of the input
                    const validators: ValidatorFn[] = [];
                    if (mandatory && inputDefinition.type !== ConfigurationInputType.BOOLEAN) {
                        validators.push(Validators.required);
                    }
                    if (inputDefinition.min_value !== null) {
                        validators.push(Validators.min(inputDefinition.min_value));
                    }
                    if (inputDefinition.max_value !== null) {
                        validators.push(Validators.max(inputDefinition.max_value));
                    }

                    const initialValue = initialValues[inputDefinition.name] ?? inputDefinition.default_value;
                    group[inputDefinition.name] = new FormControl({value: initialValue, disabled: this.disabled} , validators)
                }
            });
        }

        if (groupDefinition.configurations) {
            this.createGroups(group, groupDefinition.configurations, initialValues, dataConfig);
        }
        if (groupDefinition.options) {
            this.createGroups(group, groupDefinition.options, initialValues, dataConfig);
        }

        return new FormGroup(group);
    }

    private isMandatory(inputDefinition: { mandatory?: boolean | null }): boolean {
        return inputDefinition.mandatory !== false;
    }

    private createNamedListItemGroup(
        item: {name?: string, description?: string},
        disabled: boolean,
    ): FormGroup {
        return new FormGroup({
            name: new FormControl({value: item?.name ?? "", disabled}, Validators.required),
            description: new FormControl({value: item?.description ?? "", disabled}),
        });
    }

    private resolveAdditionalConfigs(additionalConfigs: ConfigurationAdditionalConfigs | null): ConfigurationAdditionalConfigs | null {
        if (additionalConfigs === null) {
            return null;
        }

        const configs = additionalConfigs.configs.filter(config => {
            const matchesAlgorithmName = config.applicableAlgorithmNames === null
                || config.applicableAlgorithmNames.includes(this.algorithm.name);
            const matchesPredicate = config.predicate == null
                || config.predicate(this.algorithm, this.dataConfiguration);

            return matchesAlgorithmName && matchesPredicate;
        });

        if (configs.length === 0) {
            return null;
        }

        return new ConfigurationAdditionalConfigs(configs);
    }

    /**
     * Removes all configuration properties of the given object that belongs to a group that is not selected.
     * @param object The configuration object to be modified.
     * @private
     */
    private removeUncheckedGroups(object: any): any {
        for (const group of this.groups) {
            group.removeInactiveGroups(object);
        }

        return object;
    }
}
