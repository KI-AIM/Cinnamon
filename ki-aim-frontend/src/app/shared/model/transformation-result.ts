import { Type } from "class-transformer";
import { DataSet } from "./data-set";
import { DataRowTransformationError } from "./data-row-transformation-error";

export class TransformationResult {
    dataSet: DataSet; 

    @Type(() => DataRowTransformationError)
    transformationErrors: DataRowTransformationError[] = []; 
}
