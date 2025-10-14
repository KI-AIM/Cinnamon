export enum DataScale {
    DATE = "DATE",
    NOMINAL = "NOMINAL",
    ORDINAL = "ORDINAL",
    INTERVAL = "INTERVAL",
    RATIO = "RATIO",
}

/**
 * Metadata for data scales.
 */
interface DataScaleAttributes {
    /**
     * Name for displaying the scale in the UI.
     */
    displayName: string;
}

/**
 * Additional metadata for each data type.
 */
export const DataScaleMetadata: Record<DataScale, DataScaleAttributes> = {
    [DataScale.NOMINAL]: {
        displayName: 'Nominal',
    },
    [DataScale.ORDINAL]: {
        displayName: 'Ordinal',
    },
    [DataScale.INTERVAL]: {
        displayName: 'Interval',
    },
    [DataScale.RATIO]: {
        displayName: 'Ratio',
    },
    [DataScale.DATE]: {
        displayName: 'Date',
    },
}
