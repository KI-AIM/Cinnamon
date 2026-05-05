import { Component, Input, OnInit } from '@angular/core';
import { FormGroup } from "@angular/forms";
import { AdditionalConfigurationGroup } from "@shared/interfaces/AdditionalConfigurationGroup";
import { Algorithm } from "@shared/model/algorithm";
import { AlgorithmService } from "@shared/services/algorithm.service";
import { map, Observable } from "rxjs";
import { TextSynthesisConfigurationService } from "../../services/text-synthesis-configuration.service";

@Component({
    selector: 'app-text-synthesis-configuration',
    templateUrl: './text-synthesis-configuration.component.html',
    standalone: false,
})
export class TextSynthesisConfigurationComponent implements AdditionalConfigurationGroup, OnInit {
    @Input() public disabled!: boolean;
    @Input() public form!: FormGroup;

    protected textSynthesizerAlgorithms$!: Observable<Algorithm[]>;

    constructor(
        private readonly algorithmService: AlgorithmService,
        protected readonly textSynthesisConfigurationService: TextSynthesisConfigurationService,
    ) {
    }

    public ngOnInit(): void {
        this.textSynthesizerAlgorithms$ = this.algorithmService.algorithms.pipe(
            map(algorithms => algorithms.filter(algorithm => algorithm.name.includes("text"))),
        );
    }

    public patchValue(configs: any): void {
        const current = this.form.controls[this.textSynthesisConfigurationService.formGroupName] as FormGroup;
        const disabled = this.disabled;
        current.patchValue(
            this.textSynthesisConfigurationService.createGroup(configs ?? null, disabled).getRawValue(),
            {emitEvent: false},
        );
    }

    protected get textConfigGroup(): FormGroup {
        return this.form.controls[this.textSynthesisConfigurationService.formGroupName] as FormGroup;
    }
}
