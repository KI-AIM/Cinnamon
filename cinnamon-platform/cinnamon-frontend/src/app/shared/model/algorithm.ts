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
    }
}

function getDataModality(algorithm: Algorithm): "structured_only" | "text_only" | "mixed" {
    const modality = algorithm.processing_capabilities?.data_modality;
    if (modality != null) {
        return modality;
    }

    if (algorithm.name.includes("text_only")) {
        return "text_only";
    }
    if (algorithm.name.includes("mixed_data")) {
        return "mixed";
    }

    return "structured_only";
}

export function supportsStructuredData(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) !== "text_only";
}

export function supportsFreeTextData(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) !== "structured_only";
}

export function isTextOnlySynthesizer(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) === "text_only";
}

export function isStructuredOnlySynthesizer(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) === "structured_only";
}

export function isMixedDataSynthesizer(algorithm: Algorithm): boolean {
    return getDataModality(algorithm) === "mixed";
}
