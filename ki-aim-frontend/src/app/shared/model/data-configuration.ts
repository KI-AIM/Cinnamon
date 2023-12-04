import { DataType } from "./data-type"
import { ColumnConfiguration } from "./column-configuration"
import { Type, plainToClass, Expose } from "class-transformer";
import 'reflect-metadata';

export class DataConfiguration {
    dataTypes: DataType[] = []; 

    @Type(() => ColumnConfiguration)
    configurations: ColumnConfiguration[] = []; 
    
    addColumnConfiguration(columnConfiguration: ColumnConfiguration) {
        this.configurations.push(columnConfiguration); 
    }

}
