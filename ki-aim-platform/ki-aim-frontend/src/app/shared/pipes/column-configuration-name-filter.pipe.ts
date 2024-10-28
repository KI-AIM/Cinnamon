import {Pipe, PipeTransform} from "@angular/core";
import {ColumnConfiguration} from "../model/column-configuration";

@Pipe({
    name: 'columnConfigurationNameFilter',
})
export class ColumnConfigurationNameFilterPipe implements PipeTransform {

    transform(columnConfigurations: ColumnConfiguration[], filterText: string): ColumnConfiguration[] {
        if (!columnConfigurations) {
            return [];
        }
        if (!filterText) {
            return columnConfigurations;
        }
        return columnConfigurations.filter(columnConfiguration => {
            return columnConfiguration.name.includes(filterText);
        });
    }

}
