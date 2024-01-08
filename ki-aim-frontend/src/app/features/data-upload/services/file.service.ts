export class FileService {
	file: File;

	constructor() {}

	public getFile(): File {
		return this.file;
	}

	public setFile(value: File) {
		this.file = value;
	}
}
