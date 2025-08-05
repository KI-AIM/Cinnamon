export enum Steps {
	WELCOME,
	UPLOAD,
	DATA_CONFIG,
	VALIDATION,
	ANONYMIZATION,
    SYNTHETIZATION,
    EXECUTION,
    TECHNICAL_EVALUATION,
    RISK_EVALUATION,
    EVALUATION,
}

export interface StepDefinition {
    path: string;
    id: string;
    text: string;
    enum: Steps;
    dependsOn: Steps | null;
    lockedAfter: Steps | null;
    index: number;
    stageName?: string;
}

export const StepConfiguration: Record<string, StepDefinition> = {
	WELCOME: {
		path: "/start",
		id: "navLinkStart",
		text: "Welcome",
		enum: Steps.WELCOME,
		dependsOn: null,
        lockedAfter: null,
		index: 0,
	},
	UPLOAD: {
		path: "/upload",
		id: "navLinkUpload",
		text: "Upload data",
		enum: Steps.UPLOAD,
		dependsOn: Steps.WELCOME,
        lockedAfter: Steps.VALIDATION,
		index: 1,
	},
	DATA_CONFIG: {
		path: "/dataConfiguration",
		id: "navLinkDataConfiguration",
		text: "Data configuration",
		enum: Steps.DATA_CONFIG,
		dependsOn: Steps.UPLOAD,
        lockedAfter: Steps.VALIDATION,
		index: 2,
	},
	VALIDATION: {
		path: "/dataValidation",
		id: "navLinkDataValidation",
		text: "Data validation",
		enum: Steps.VALIDATION,
		dependsOn: Steps.DATA_CONFIG,
        lockedAfter: Steps.VALIDATION,
		index: 3,
	},
	ANONYMIZATION: {
		path: "/anonymizationConfiguration",
		id: "navLinkAnonymizationConfiguration",
		text: "Anonymization configuration",
		enum: Steps.ANONYMIZATION,
		dependsOn: Steps.VALIDATION,
        lockedAfter: Steps.EXECUTION,
		index: 4,
	},
    SYNTHETIZATION: {
        path: "/synthetizationConfiguration",
        id: "navLinkSynthetizationConfiguration",
        text: "Synthetization configuration",
        enum: Steps.SYNTHETIZATION,
        dependsOn: Steps.ANONYMIZATION,
        lockedAfter: Steps.EXECUTION,
        index: 5,
    },
    EXECUTION: {
        path: "/execution",
        id: "navLinkExecution",
        text: "Execution",
        enum: Steps.EXECUTION,
        dependsOn: Steps.SYNTHETIZATION,
        lockedAfter: Steps.EXECUTION,
        index: 6,
        stageName: "execution",
    },
    TECHNICAL_EVALUATION : {
        path: "/technicalEvaluationConfiguration",
        id: "navLinkTechnicalEvalutionConfiguration",
        text: "Technical Evaluation configuration",
        enum: Steps.TECHNICAL_EVALUATION,
        dependsOn: Steps.EXECUTION,
        lockedAfter: Steps.EVALUATION,
        index: 7,
    },
    RISK_EVALUATION : {
        path: "/riskEvaluationConfiguration",
        id: "navLinkRiskEvalutionConfiguration",
        text: "Risk Evaluation configuration",
        enum: Steps.RISK_EVALUATION,
        dependsOn: Steps.TECHNICAL_EVALUATION,
        lockedAfter: Steps.EVALUATION,
        index: 8,
    },
    EVALUATION : {
        path: "/evaluation",
        id: "navLinkEvalution",
        text: "Evaluation",
        enum: Steps.EVALUATION,
        dependsOn: Steps.RISK_EVALUATION,
        lockedAfter: Steps.EVALUATION,
        index: 9,
        stageName: "evaluation",
    },
};

export function getStepDefinition(step: Steps): StepDefinition | null {
    for (const def of Object.values(StepConfiguration)) {
        if (def.enum === step) {
            return def;
        }
    }
    return null;
}
