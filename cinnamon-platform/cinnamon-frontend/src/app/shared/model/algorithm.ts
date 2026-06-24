export class Algorithm {
    name: string
    display_name: string
    description: string
    // TODO enum?
    type: string
    version: string
    URL: string
    processing_capabilities?: {
        data_modality?: "structured_only" | "text_only" | "mixed"
        generation_scope?: "structured_only" | "text_only"
        supports_structured_data?: boolean
        supports_free_text_data?: boolean
    }
}

function getDataModality(algorithm: Algorithm): "structured_only" | "text_only" | "mixed" {
    const modality = algorithm.processing_capabilities?.data_modality;
    if (modality != null) {
        return modality;
    }

    const supportsStructured = algorithm.processing_capabilities?.supports_structured_data;
    const supportsFreeText = algorithm.processing_capabilities?.supports_free_text_data;
    if (supportsStructured === false && supportsFreeText === true) {
        return "mixed";
    }

    return algorithm.name.includes("text") ? "mixed" : "structured_only";
}

function getGenerationScope(algorithm: Algorithm): "structured_only" | "text_only" {
    const scope = algorithm.processing_capabilities?.generation_scope;
    if (scope != null) {
        return scope;
    }

    const supportsFreeText = algorithm.processing_capabilities?.supports_free_text_data;
    if (supportsFreeText != null) {
        return supportsFreeText ? "text_only" : "structured_only";
    }

    return algorithm.name.includes("text") ? "text_only" : "structured_only";
}

export function supportsStructuredData(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) !== "text_only";
}

export function supportsFreeTextData(algorithm: Algorithm): boolean {
    return getGenerationScope(algorithm) === "text_only";
}

export function isTextOnlySynthesizer(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) === "text_only" && getGenerationScope(algorithm) === "text_only";
}

export function isStructuredOnlySynthesizer(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) === "structured_only" && getGenerationScope(algorithm) === "structured_only";
}

export function isMixedTextSynthesizer(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) === "mixed" && getGenerationScope(algorithm) === "text_only";
}
