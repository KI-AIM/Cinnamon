import { Injectable } from '@angular/core';
import { AlgorithmService } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { Algorithm } from "../../../shared/model/algorithm";

@Injectable()
export class SynthetizationService extends AlgorithmService {

    constructor(
        private readonly http2: HttpClient,
    ) {
        super(http2);
    }

    public override getStepName = (): string => "synthetization";

    public override getConfigurationName = (): string => "synthetization";

    public override getDefinitionUrl = (algorithm: Algorithm) => `/synthetic_${algorithm.type}_data_generator/synthesizer_config/${algorithm.name}.yaml`;
}
