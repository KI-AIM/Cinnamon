import { Type } from "class-transformer";
import { DataTransformationError } from "./data-transformation-error";

export class DataRowTransformationError {
    index: number;
    @Type(() => DataTransformationError)
    dataTransformationErrors: DataTransformationError[] = [];
}
