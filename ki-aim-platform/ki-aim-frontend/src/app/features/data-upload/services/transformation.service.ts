import { TransformationResult } from "src/app/shared/model/transformation-result";

export class TransformationService {
    private transformationResult: TransformationResult;

	constructor(
    ) {
        this.transformationResult = new TransformationResult();
    }

    getTransformationResult(): TransformationResult {
        return this.transformationResult;
    }

    setTransformationResult(value: TransformationResult) {
        this.transformationResult = value;
    }
}
