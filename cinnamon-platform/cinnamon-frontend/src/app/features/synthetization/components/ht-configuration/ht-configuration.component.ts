import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { AlgorithmDefinition } from '../../../../shared/model/algorithm-definition';
import { ConfigurationInputDefinition } from '../../../../shared/model/configuration-input-definition';

@Component({
    selector: 'app-ht-configuration',
    templateUrl: './ht-configuration.component.html',
    styleUrls: ['./ht-configuration.component.less'],
    standalone: false,
})
export class HtConfigurationComponent {

    /** Whether hyperparameter tuning is enabled. */
    @Input() enabled = false;

    /** Fired when the checkbox changes. */
    @Output() enabledChange = new EventEmitter<boolean>();

    /**
     * The full HT FormGroup: `{ study: { sampler, pruner, n_trials,
     * timeout_minutes, target_variable } }`.
     */
    @Input() htFormGroup: FormGroup | null = null;

    /**
     * Study definition loaded from `study.yaml`.
     * Used to supply `ConfigurationInputDefinition` objects to
     * `app-configuration-input` for the numeric fields.
     */
    @Input() studyDefinition: AlgorithmDefinition | null = null;

    /** Convenience accessor for the nested `study` FormGroup. */
    get studyGroup(): FormGroup | null {
        const g = this.htFormGroup?.get('study');
        return (g instanceof FormGroup) ? g : null;
    }

    /** Returns the parameter definition for the given field name from study.yaml. */
    getParam(name: string): ConfigurationInputDefinition | null {
        const params: ConfigurationInputDefinition[] =
            this.studyDefinition?.configurations?.['study']?.parameters ?? [];
        return params.find(p => p.name === name) ?? null;
    }
}
