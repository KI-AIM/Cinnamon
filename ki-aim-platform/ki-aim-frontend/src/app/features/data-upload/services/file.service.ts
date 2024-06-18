import { FileConfiguration, FileType } from "../../../shared/model/file-configuration";
import { CsvFileConfiguration, Delimiter, LineEnding, QuoteChar } from "../../../shared/model/csv-file-configuration";
import { XlsxFileConfiguration } from "src/app/shared/model/xlsx-file-configuration";

export class FileService {
	file: File;
    fileConfiguration: FileConfiguration;

	constructor() {
		this.fileConfiguration = new FileConfiguration(FileType.CSV, new CsvFileConfiguration(Delimiter.COMMA, LineEnding.LF, QuoteChar.DOUBLE_QUOTE, true), new XlsxFileConfiguration(true));
	}

	public getFile(): File {
		return this.file;
	}

	public setFile(value: File) {
		this.file = value;
	}

    public getFileConfiguration(): FileConfiguration {
        return this.fileConfiguration;
    }

	public setFileConfiguration(value: FileConfiguration) {
		this.fileConfiguration = value;
	}
}
