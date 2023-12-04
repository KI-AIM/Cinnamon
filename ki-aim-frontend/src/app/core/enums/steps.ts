export enum Steps {
    WELCOME,
    UPLOAD,
    DATA_CONFIG,
    VALIDATION,
    PERSISTING
}

export const StepConfiguration = {
    WELCOME: {"path": "/start", "id": "navLinkStart", "text": "Welcome", "enum": Steps.WELCOME, "dependsOn": "", "index": 0},
    UPLOAD: {"path": "/upload", "id": "navLinkUpload", "text": "Upload data", "enum": Steps.UPLOAD, "dependsOn": Steps.WELCOME,  "index": 1},
    DATA_CONFIG: {"path": "/dataConfiguration", "id": "navLinkDataConfiguration", "text": "Data configuration", "enum": Steps.DATA_CONFIG, "dependsOn": Steps.UPLOAD,  "index": 2},
    //VALIDATION: {"path": "", "id": "", "text": "", "enum": Steps.VALIDATION, "index": 3},
    //PERSISTING: {"path": "", "id": "", "text": "", "enum": Steps.PERSISTING, "index": 4},
}
