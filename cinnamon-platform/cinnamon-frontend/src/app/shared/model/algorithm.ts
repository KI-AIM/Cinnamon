export class Algorithm {
    name: string
    display_name: string
    description: string
    // TODO enum?
    type: string
    version: string
    URL: string
    processing_capabilities?: {
        supports_structured_data?: boolean
        supports_free_text_data?: boolean
    }
}

export function supportsStructuredData(algorithm: Algorithm): boolean {
    const supportsStructured = algorithm.processing_capabilities?.supports_structured_data;
    if (supportsStructured === undefined) {
        return !algorithm.name.includes("text");
    }
    return supportsStructured;
}

export function supportsFreeTextData(algorithm: Algorithm): boolean {
    const supportsFreeText = algorithm.processing_capabilities?.supports_free_text_data;
    if (supportsFreeText === undefined) {
        return algorithm.name.includes("text");
    }
    return supportsFreeText;
}

export function isTextOnlySynthesizer(algorithm: Algorithm): boolean {
    const supportsStructured = algorithm.processing_capabilities?.supports_structured_data;
    const supportsFreeText = algorithm.processing_capabilities?.supports_free_text_data;

    if (supportsStructured !== undefined || supportsFreeText !== undefined) {
        return supportsStructured === false && supportsFreeText === true;
    }

    return algorithm.name.includes("text");
}
