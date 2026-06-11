import { Component, OnDestroy, OnInit } from '@angular/core';
import { Steps } from "@core/enums/steps";
import { TitleService } from "../../../../core/services/title-service.service";
import { AlgorithmService, ConfigurationInfo } from "../../../../shared/services/algorithm.service";
import { SynthetizationService } from "../../services/synthetization.service";
import { Observable, Subject, takeUntil } from "rxjs";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { AlgorithmDefinition } from "../../../../shared/model/algorithm-definition";
import { ConfigurationInputDefinition } from "../../../../shared/model/configuration-input-definition";

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
export class SynthetizationConfigurationComponent implements OnInit, OnDestroy {
    protected readonly Steps = Steps;

    protected configurationInfo$: Observable<ConfigurationInfo>;

    /** Whether Optuna hyperparameter tuning is enabled. Default off. */
    protected useHyperparameterTuning = false;

    /** Definition fetched from `study.yaml`. Null until loaded. */
    protected studyDefinition: AlgorithmDefinition | null = null;

    /** FormGroup built dynamically from `studyDefinition`. */
    protected htFormGroup: FormGroup | null = null;

    /** HT config from an uploaded/loaded file, applied once the form is built. */
    private pendingHtConfig: any = null;

    private readonly destroy$ = new Subject<void>();

    constructor(
        private readonly synthService: SynthetizationService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Synthetization");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.synthService.fetchInfo();

        this.synthService.loadStudyDefinition()
            .pipe(takeUntil(this.destroy$))
            .subscribe((def: AlgorithmDefinition) => {
                this.studyDefinition = def;
                this.htFormGroup = this.buildHtFormGroup(def);
                this.htFormGroup.valueChanges
                    .pipe(takeUntil(this.destroy$))
                    .subscribe(() => this.pushHtConfig());
                // Apply a config that arrived before the form existed, else push
                // the form defaults.
                if (this.pendingHtConfig) {
                    this.applyLoadedHtConfig(this.pendingHtConfig);
                } else {
                    this.pushHtConfig();
                }
            });

        // Hydrate the toggle + form when a configuration is uploaded/loaded.
        this.synthService.hyperparameterConfigLoaded$
            .pipe(takeUntil(this.destroy$))
            .subscribe((cfg) => this.applyLoadedHtConfig(cfg));
    }

    public ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    protected setHyperparameterTuning(enabled: boolean): void {
        this.useHyperparameterTuning = enabled;
        this.pushHtConfig();
    }

    private buildHtFormGroup(def: AlgorithmDefinition): FormGroup {
        // These fields are optional — leave empty for "no limit" / auto-derive.
        const optionalFields = ['timeout_minutes', 'target_variable'];
        const inner: Record<string, FormControl> = {};
        const params: ConfigurationInputDefinition[] = def.configurations?.['study']?.parameters ?? [];
        for (const p of params) {
            const validators = optionalFields.includes(p.name) ? [] : [Validators.required];
            if (p.min_value !== null && p.min_value !== undefined) {
                validators.push(Validators.min(p.min_value));
            }
            if (p.max_value !== null && p.max_value !== undefined) {
                validators.push(Validators.max(p.max_value));
            }
            inner[p.name] = new FormControl(p.default_value, validators);
        }
        return new FormGroup({ study: new FormGroup(inner) });
    }

    /**
     * Applies a hyperparameter-tuning config from an uploaded/loaded file to the
     * toggle and the form. If the form is not built yet (study.yaml still
     * loading), defers until it is.
     */
    private applyLoadedHtConfig(cfg: any): void {
        if (!cfg) {
            return;
        }
        if (!this.htFormGroup) {
            this.pendingHtConfig = cfg;
            return;
        }
        this.pendingHtConfig = null;

        this.useHyperparameterTuning = !!cfg.enabled;

        const studyGroup = this.htFormGroup.get('study') as FormGroup | null;
        if (studyGroup) {
            const { enabled, ...studyValues } = cfg;
            // patchValue ignores keys absent from the form (e.g. target_variable).
            studyGroup.patchValue(studyValues, { emitEvent: false });
        }
        this.pushHtConfig();
    }

    private pushHtConfig(): void {
        const studyValues = (this.htFormGroup?.value?.study ?? {}) as Record<string, unknown>;
        this.synthService.setHyperparameterConfig({
            enabled: this.useHyperparameterTuning,
            ...studyValues,
        });
    }
}
