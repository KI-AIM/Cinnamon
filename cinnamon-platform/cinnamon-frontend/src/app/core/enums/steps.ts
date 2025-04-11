export enum Steps {
    WELCOME = "WELCOME",
    UPLOAD = "UPLOAD",
    DATA_CONFIG = "DATA_CONFIG",
    VALIDATION = "VALIDATION",
    ANONYMIZATION = "ANONYMIZATION",
    SYNTHETIZATION = "SYNTHETIZATION",
    EXECUTION = "EXECUTION",
    TECHNICAL_EVALUATION = "TECHNICAL_EVALUATION",
    RISK_EVALUATION = "RISK_EVALUATION",
    EVALUATION = "EVALUATION",
    REPORT = "REPORT",
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

export const StepConfiguration: Record<Steps, StepDefinition> = {
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
    REPORT: {
        path: "/report",
        id: "navLinkReport",
        text: "Report",
        enum: Steps.REPORT,
        dependsOn: Steps.EVALUATION,
        lockedAfter: null,
        index: 10,
    }
};
