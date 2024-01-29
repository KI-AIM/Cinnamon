import { ColumnConfiguration } from "./column-configuration"
import { Type } from "class-transformer";
import 'reflect-metadata';

export class DataConfiguration {
    @Type(() => ColumnConfiguration)
    configurations: ColumnConfiguration[] = []; 
    
    addColumnConfiguration(columnConfiguration: ColumnConfiguration) {
        this.configurations.push(columnConfiguration); 
    }

}
