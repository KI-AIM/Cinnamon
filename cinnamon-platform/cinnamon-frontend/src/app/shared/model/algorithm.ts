export class Algorithm {
    class: string
    description: string
    name: string
    display_name: string
    // TODO enum?
    type: string
    version: string
    URL: string
    processing_capabilities?: {
        supports_structured_data?: boolean
        supports_free_text_data?: boolean
    }
}
