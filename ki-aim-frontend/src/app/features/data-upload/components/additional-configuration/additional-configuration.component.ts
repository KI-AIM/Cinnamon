import { Component, Input } from '@angular/core';
import { List } from 'src/app/core/utils/list';
import { ConfigurationTypes, DataTypeToConfigurationTypeMapping } from 'src/app/shared/model/configuration-types';
import { DataType } from 'src/app/shared/model/data-type';

@Component({
    selector: 'app-additional-configuration',
    templateUrl: './additional-configuration.component.html',
    styleUrls: ['./additional-configuration.component.less'],
})
export class AdditionalConfigurationComponent {
    @Input() attrNumber: Number; 

    getConfigurationsForDatatype(type: DataType) {
        var result = new List<ConfigurationTypes>(); 

        var typeIndex = Object.keys(DataType).indexOf(type.toString());

        var mappedConfigurationsTypes = DataTypeToConfigurationTypeMapping
    }
}
