import { TransformationResult } from "src/app/shared/model/transformation-result";

export class TransformationService {
    private transformationResult: TransformationResult;

	constructor() {

    }

    getTransformationResult(): TransformationResult {
        return this.transformationResult; 
    }

    setTransformationResult(value: TransformationResult) {
        this.transformationResult = value; 
    }
}
