export enum LineEnding {
    CR = "\r",
    CRLF = "\r\n",
    LF = "\n",
}

export enum Delimiter {
    COMMA = ",",
    SEMICOLON = ";",
}

export class CsvFileConfiguration {
    constructor(
        public columnSeparator: Delimiter,
        public lineSeparator: LineEnding,
        public hasHeader: boolean,
    ) {}

}
