import {Injector, Pipe, PipeTransform} from "@angular/core";
import {ColumnConfiguration} from "../model/column-configuration";

@Pipe({name: 'columnConfigurationNameFilter'})
export class ColumnConfigurationNameFilter implements PipeTransform {

    public constructor(private readonly injector: Injector) {
    }

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
