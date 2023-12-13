import { FileConfiguration } from "../../../shared/model/file-configuration";
import { CsvFileConfiguration } from "../../../shared/model/csv-file-configuration";

export class FileService {
	file: File;
    fileConfiguration: FileConfiguration;

	constructor() {
        this.fileConfiguration = new FileConfiguration("CSV", new CsvFileConfiguration(",", "\n", true));
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
}
