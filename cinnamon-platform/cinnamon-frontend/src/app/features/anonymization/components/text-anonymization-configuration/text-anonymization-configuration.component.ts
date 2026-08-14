import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { AdditionalConfigurationGroup } from '@shared/interfaces/AdditionalConfigurationGroup';
import { DataType } from '@shared/model/data-type';
import { DataConfigurationService } from '@shared/services/data-configuration.service';
import { Subscription } from 'rxjs';
import { TextAnonymizationConfigurationService } from '../../services/text-anonymization-configuration.service';
import { AnonymizationMode, ModelType } from '@shared/model/text-anonymization.types';

@Component({
    selector: 'app-text-anonymization-configuration',
    templateUrl: './text-anonymization-configuration.component.html',
    standalone: false,
})
export class TextAnonymizationConfigurationComponent implements AdditionalConfigurationGroup, OnInit, OnDestroy {
    @Input() public disabled!: boolean;
    @Input() public form!: FormGroup;
    protected readonly AnonymizationMode = AnonymizationMode;
    protected readonly modelTypes: ModelType[] = Object.values(ModelType);

    protected textColumns: Array<{name: string, index: number}> = [];
    private dataConfigurationSubscription?: Subscription;
    private textColumnsInitialized = false;

    constructor(
        private readonly dataConfigurationService: DataConfigurationService,
        protected readonly textAnonymizationConfigurationService: TextAnonymizationConfigurationService,
    ) {
    }

    public ngOnInit(): void {
        this.dataConfigurationSubscription = this.dataConfigurationService.dataConfiguration$.subscribe(configuration => {
            this.textColumns = configuration.configurations
                .filter(column => column.type === DataType.TEXT)
                .map(column => ({name: column.name, index: column.index}));
            this.selectAllTextColumnsIfEmpty();
        });
    }

    public ngOnDestroy(): void {
        this.dataConfigurationSubscription?.unsubscribe();
    }

    public patchValue(config: any): void {
        this.textConfigGroup.patchValue(
            this.textAnonymizationConfigurationService.createGroup(config, this.disabled).getRawValue(),
            {emitEvent: false},
        );
        this.selectAllTextColumnsIfEmpty();
    }

    protected get textConfigGroup(): FormGroup {
        return this.form.controls[this.textAnonymizationConfigurationService.formGroupName] as FormGroup;
    }

    private selectAllTextColumnsIfEmpty(): void {
        if (this.textColumnsInitialized || !this.form || this.textColumns.length === 0) {
            return;
        }

        const columnsControl = this.textConfigGroup.get('columns');
        const selectedColumns = columnsControl?.value;
        if (!columnsControl) {
            return;
        }
        if (!Array.isArray(selectedColumns) || selectedColumns.length === 0) {
            columnsControl.setValue(this.textColumns.map(column => column.name), {emitEvent: false});
        }
        this.textColumnsInitialized = true;
    }
}
