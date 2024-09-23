import { Component, ComponentRef, OnInit, ViewChild } from '@angular/core';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { DataConfiguration } from 'src/app/shared/model/data-configuration';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { AnonymizationAttributeConfigurationDirectiveDirective } from '../../directives/anonymization-attribute-configuration-directive.directive';
import { AnonymizationAttributeRowComponent } from '../anonymization-attribute-row/anonymization-attribute-row.component';
import { AnonymizationAttributeRowConfiguration } from 'src/app/shared/model/anonymization-attribute-row-configuration';
import { AnonymizationAttributeConfigurationService } from '../../services/anonymization-attribute-configuration.service';
import { MatSelect } from '@angular/material/select';
@Component({
    selector: 'app-anonymization-attribute-configuration',
    templateUrl: './anonymization-attribute-configuration.component.html',
    styleUrls: ['./anonymization-attribute-configuration.component.css'],
})
export class AnonymizationAttributeConfigurationComponent implements OnInit {
    @ViewChild("attributeDropdown") attributeDropdown: MatSelect; 
    @ViewChild(AnonymizationAttributeConfigurationDirectiveDirective, {
        static: true,
    })
    target: AnonymizationAttributeConfigurationDirectiveDirective;
    dataConfiguration: DataConfiguration;
    availableConfigurations: ColumnConfiguration[];

    constructor(
        public configuration: DataConfigurationService,
        public attributeConfigurationService: AnonymizationAttributeConfigurationService
    ) {}
    

    ngOnInit() {
        this.dataConfiguration = this.configuration.getDataConfiguration();
        this.availableConfigurations = this.dataConfiguration.configurations;
    }

    onSelectionChange(value: any) {
        const selectedRowIndex = value;
        const selectedRow = this.availableConfigurations.find(
            (row) => row.index === selectedRowIndex
        );

        if (selectedRow !== null && selectedRow !== undefined) {
            const viewContainerRef = this.target.viewContainerRef;
            const newComponentRef = viewContainerRef.createComponent(
                AnonymizationAttributeRowComponent
            );

            newComponentRef.instance.configurationRow = selectedRow;

            let newRowConfiguration =
                new AnonymizationAttributeRowConfiguration();

            newRowConfiguration.attributeIndex = selectedRow.index;
            newRowConfiguration.attributeName = selectedRow.name;

            newComponentRef.instance.anonymizationRowConfiguration =
                newRowConfiguration;
            this.attributeConfigurationService.addRowConfiguration(
                newRowConfiguration
            );

            newComponentRef.instance.removeEvent.subscribe(() => {
                this.removeSelectedAttribute(newComponentRef);
            });

            this.removeAttributeFromDropdown(selectedRow);
            this.attributeDropdown.value = undefined; 
        }
    }

    removeAttributeFromDropdown(rowToRemove: ColumnConfiguration) {
        this.availableConfigurations = this.availableConfigurations.filter(
            (row) => row.index !== rowToRemove.index
        );
        this.sortAvailabelConfigurationById();
    }

    removeSelectedAttribute(
        rowToRemove: ComponentRef<AnonymizationAttributeRowComponent>
    ) {
        this.availableConfigurations.push(
            rowToRemove.instance.configurationRow
        );

        let indexToDelete =
            rowToRemove.instance.anonymizationRowConfiguration.attributeIndex;
        this.attributeConfigurationService.removeRowConfigurationById(
            indexToDelete
        );

        rowToRemove.destroy();
        this.sortAvailabelConfigurationById();
    }

    sortAvailabelConfigurationById() {
        this.availableConfigurations.sort((a, b) => a.index - b.index);
    }
}
