import { Component, OnInit, ViewChild } from '@angular/core';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { DataConfiguration } from 'src/app/shared/model/data-configuration';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { AnonymizationAttributeConfigurationDirective } from '../../directives/anonymization-attribute-configuration.directive';
import { AnonymizationAttributeConfigurationService } from '../../services/anonymization-attribute-configuration.service';
import { MatSelect } from '@angular/material/select';
import { AnonymizationAttributeConfiguration, AnonymizationAttributeRowConfiguration } from 'src/app/shared/model/anonymization-attribute-config';
import { Subscription } from "rxjs";
@Component({
    selector: 'app-anonymization-attribute-configuration',
    templateUrl: './anonymization-attribute-configuration.component.html',
    styleUrls: ['./anonymization-attribute-configuration.component.less'],
})
export class AnonymizationAttributeConfigurationComponent implements OnInit {
    @ViewChild("attributeDropdown") attributeDropdown: MatSelect;
    @ViewChild(AnonymizationAttributeConfigurationDirective, {
        static: true,
    })
    target: AnonymizationAttributeConfigurationDirective;
    dataConfiguration: DataConfiguration;
    error: string | null = null;

    private dataConfigurationSubscription: Subscription;

    constructor(
        public configuration: DataConfigurationService,
        public attributeConfigurationService: AnonymizationAttributeConfigurationService
    ) {
    }


    ngOnInit() {
        this.dataConfigurationSubscription = this.configuration.dataConfiguration$.subscribe(value => {
            this.dataConfiguration = value;
        });
    }

    ngOnDestroy() {
        this.dataConfigurationSubscription.unsubscribe();
    }

    /**
     * Returns currently stored anonymization attribute configuration
     * @returns AnonymizationAttributeConfiguration | null
     */
    getAttributeConfiguration(): AnonymizationAttributeConfiguration | null {
        return this.attributeConfigurationService.getAttributeConfiguration();
    }

    hasAttributeConfiguration(): boolean {
        let config = this.attributeConfigurationService.getAttributeConfiguration();
        if (config !== null) {
            if (config.attributeConfiguration.length > 0) {
                return true
            }
        }
        return false;
    }

    /**
     * Filters the dataConfiguration ColumnConfiguration list
     * by removing all entries with attributes that are currently
     * used in the anonymization attribute configuration
     * @returns Array<ColumnConfiguration>
     */
    getAvailableConfigurations() {
        const indicesAlreadyUsed = new Set(this.attributeConfigurationService.getAttributeConfiguration()?.attributeConfiguration.map(item => item.index));

        if (this.dataConfiguration !== undefined && this.dataConfiguration !== null) {
            return this.dataConfiguration.configurations.filter(item => !indicesAlreadyUsed.has(item.index))
        } else {
            return new Array<ColumnConfiguration>();
        }
    }

    /**
     * Returns the available column configurations
     * sorted by their index attribute
     * @returns Array<ColumnConfiguration>
     */
    getAvailableConfigurationsSortedById() {
        return this.getAvailableConfigurations().sort((a, b) => a.index - b.index);
    }

    /**
     * Event that is triggered by selecting another
     * column in the dropdown.
     *
     * Adds a new entry to the anonymization attribute
     * configuration, thus removing it from the dropdown
     * automatically.
     * @param value
     */
    onSelectionChange(value: any) {
        const selectedRowIndex = value;
        const selectedRow = this.getAvailableConfigurations().find(
            (row) => row.index === selectedRowIndex
        );

        if (selectedRow !== null && selectedRow !== undefined) {
            let newRowConfiguration =
                new AnonymizationAttributeRowConfiguration();

            newRowConfiguration.index = selectedRow.index;
            newRowConfiguration.name = selectedRow.name;
            newRowConfiguration.dataType = selectedRow.type;
            newRowConfiguration.scale = selectedRow.scale;

            this.attributeConfigurationService.addRowConfiguration(newRowConfiguration);
        }
    }

    addAllAttributes() {
        this.getAvailableConfigurations().forEach(selectedRow => {
            if (selectedRow !== null && selectedRow !== undefined) {
                let newRowConfiguration =
                    new AnonymizationAttributeRowConfiguration();

                newRowConfiguration.index = selectedRow.index;
                newRowConfiguration.name = selectedRow.name;
                newRowConfiguration.dataType = selectedRow.type;
                newRowConfiguration.scale = selectedRow.scale;

                this.attributeConfigurationService.addRowConfiguration(newRowConfiguration);
            }
        }) ;
    }

    removeAllAttributes() {
        this.attributeConfigurationService.getAttributeConfiguration()?.attributeConfiguration.forEach(config => {
            this.removeAttributeConfigurationRow(config);
        });
    }

    /**
     * Returns the ColumnConfiguration for a given index
     * @param index to search for
     * @returns ColumnConfiguration, null if index is not found
     */
    getConfigurationForIndex(index: number): ColumnConfiguration | null {
        const selectedRow = this.getAvailableConfigurations().find(
            (row) => row.index === index
        );

        if (selectedRow !== null && selectedRow !== undefined) {
            return selectedRow;
        } else {
            return null
        }
    }

    /**
     * Event to remove a given anonymization attribute configuration
     * @param attributeConfigurationRow to delete
     */
    removeAttributeConfigurationRow(attributeConfigurationRow: AnonymizationAttributeRowConfiguration) {
        this.attributeConfigurationService.removeRowConfigurationById(attributeConfigurationRow.index);
    }

}
