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
    index: number;
}

export const StepConfiguration: Record<string, StepDefinition> = {
	WELCOME: {
		path: "/start",
		id: "navLinkStart",
		text: "Welcome",
		enum: Steps.WELCOME,
		dependsOn: null,
		index: 0,
	},
	UPLOAD: {
		path: "/upload",
		id: "navLinkUpload",
		text: "Upload data",
		enum: Steps.UPLOAD,
		dependsOn: Steps.WELCOME,
		index: 1,
	},
	DATA_CONFIG: {
		path: "/dataConfiguration",
		id: "navLinkDataConfiguration",
		text: "Data configuration",
		enum: Steps.DATA_CONFIG,
		dependsOn: Steps.UPLOAD,
		index: 2,
	},
	VALIDATION: {
		path: "/dataValidation",
		id: "navLinkDataValidation",
		text: "Data validation",
		enum: Steps.VALIDATION,
		dependsOn: Steps.DATA_CONFIG,
		index: 3,
	},
	ANONYMIZATION: {
		path: "anonymizationConfiguration",
		id: "navLinkAnonymizationConfiguration",
		text: "Anonymization configuration",
		enum: Steps.ANONYMIZATION,
		dependsOn: Steps.VALIDATION,
		index: 4,
	},
    SYNTHETIZATION: {
        path: "synthetizationConfiguration",
        id: "navLinkSynthetizationConfiguration",
        text: "Synthetization configuration",
        enum: Steps.SYNTHETIZATION,
        dependsOn: Steps.ANONYMIZATION,
        index: 5,
    },
    EXECUTION: {
        path: "execution",
        id: "navLinkExecution",
        text: "Execution",
        enum: Steps.EXECUTION,
        dependsOn: Steps.SYNTHETIZATION,
        index: 6,
    },
    TECHNICAL_EVALUATION : {
        path: "technicalEvaluationConfiguration",
        id: "navLinkTechnicalEvalutionConfiguration",
        text: "Technical Evaluation configuration",
        enum: Steps.TECHNICAL_EVALUATION,
        dependsOn: Steps.EXECUTION,
        index: 7,
    },
    RISK_EVALUATION : {
        path: "riskEvaluationConfiguration",
        id: "navLinkRiskEvalutionConfiguration",
        text: "Risk Evaluation configuration",
        enum: Steps.RISK_EVALUATION,
        dependsOn: Steps.TECHNICAL_EVALUATION,
        index: 8,
    },
    EVALUATION : {
        path: "evaluation",
        id: "navLinkEvalution",
        text: "Evaluation",
        enum: Steps.EVALUATION,
        dependsOn: Steps.RISK_EVALUATION,
        index: 9,
    },
};
