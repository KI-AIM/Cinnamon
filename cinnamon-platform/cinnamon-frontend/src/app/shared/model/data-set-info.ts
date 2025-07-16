export class DataSetInfo {
    numberRows: number;
    numberInvalidRows: number;
    hasHoldOutSplit: boolean;
    holdOutPercentage: number;
    numberHoldOutRows: number;
    numberInvalidHoldOutRows: number;

    public get numberNotHoldOutRows(): number {
        return this.numberRows - this.numberHoldOutRows;
    }

    public get numberInvalidNotHoldOutRows(): number {
        return this.numberInvalidRows - this.numberInvalidHoldOutRows;
    }
}
