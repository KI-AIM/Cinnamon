export class CsvFileConfiguration {
    constructor(
        public columnSeparator: string,
        public lineSeparator: string,
        public hasHeader: boolean,
    ) {}

}
