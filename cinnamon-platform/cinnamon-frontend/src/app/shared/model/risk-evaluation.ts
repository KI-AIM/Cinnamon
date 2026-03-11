import { Type, plainToInstance, Transform } from "class-transformer";

export class RiskInference {
    risk_value: number;
    @Type(() => Array)
    risk_ci: [number, number];
}

export class RiskResults {
    risk_value?: number;
    priv_risk?: number; // For inference_average_risk
    @Type(() => Array)
    risk_ci?: [number, number];
    n_attacks: number;
    n_success: number;
    n_baseline: number;
    n_control: number;
    @Type(() => Object)
    attack_rate: { value: number; error: number };
    @Type(() => Object)
    baseline_rate: { value: number; error: number };
    @Type(() => Object)
    control_rate: { value: number; error: number };
    execution_time?: number;
}

export class AttributeRiskEvaluation {
    name: string;
    @Type(() => RiskInference)
    value: RiskInference;
}

export class AttributeRiskResult {
    name: string;
    @Type(() => RiskResults)
    value: RiskResults;
}

export class InferenceAverageRiskResults {
    risk_value?: number;
    priv_risk?: number;

    n_attacks: number;
    n_success: number;
    n_baseline: number;
    n_control: number;

    execution_time?: number;

    // Spécifique à inference_average_risk
    attack_rate_success_rate: number;
    attack_rate_error: number;

    baseline_rate_success_rate: number;
    baseline_rate_error: number;

    control_rate_success_rate: number;
    control_rate_error: number;
}

export class RiskEvaluation {
    @Transform(({ value }) => value.map(([name, obj]: [string, any]) => plainToInstance(AttributeRiskEvaluation, { name, value: obj })), { toClassOnly: true })
    inference_risk?: AttributeRiskEvaluation[];

    @Transform(({ value }) => value.map(([name, obj]: [string, any]) => plainToInstance(AttributeRiskResult, { name, value: obj })), { toClassOnly: true })
    inference_results?: AttributeRiskResult[];

    @Type(() => InferenceAverageRiskResults)
    inference_average_risk?: InferenceAverageRiskResults;

    @Type(() => RiskResults)
    linkage_health_risk?: RiskResults;

    @Type(() => RiskResults)
    univariate_singling_out_risk?: RiskResults;

    @Type(() => RiskResults)
    multivariate_singling_out_risk?: RiskResults;

    total_risk_score: number;
}
