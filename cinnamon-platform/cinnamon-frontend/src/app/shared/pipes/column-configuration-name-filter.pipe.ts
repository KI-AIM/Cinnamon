import {Pipe, PipeTransform} from "@angular/core";
import {ColumnConfiguration} from "../model/column-configuration";

@Pipe({
    name: 'columnConfigurationNameFilter',
    standalone: false
})
export class ColumnConfigurationNameFilterPipe implements PipeTransform {

    transform(columnConfigurations: ColumnConfiguration[], filterText: string): ColumnConfigurationFilterResult {
        const result = new ColumnConfigurationFilterResult();

        if (!columnConfigurations) {
            return result;
        }

        result.originalCount = columnConfigurations.length

        if (!filterText) {
            result.filteredList = columnConfigurations;
            result.filteredCount = columnConfigurations.length
        } else {
            const filtered = columnConfigurations.filter(columnConfiguration => {
                return columnConfiguration.name.includes(filterText);
            });

            result.filteredList = filtered;
            result.filteredCount = filtered.length
        }

        return result;
    }

}

export class ColumnConfigurationFilterResult {
    originalCount: number = 0;
    filteredCount: number = 0;
    filteredList: ColumnConfiguration[] = [];
}
