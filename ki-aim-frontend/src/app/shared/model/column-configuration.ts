import { DataType } from "./data-type";
import { Configuration } from "./configuration";
import { Type, plainToClass, Expose } from "class-transformer";
import 'reflect-metadata';

export class ColumnConfiguration {
    index: number;
    name: String; 
    type: DataType; 

    @Type(() => Configuration)
    configurations: Configuration[] = []; 

    addConfiguration(configuration: Configuration) {
        this.configurations.push(configuration); 
    }

    removeConfiguration(configuration: Configuration) {
        var index = this.configurations.indexOf(configuration, 0);
        if (index > -1) {
            this.configurations.splice(index, 1);
        }
    }

}
