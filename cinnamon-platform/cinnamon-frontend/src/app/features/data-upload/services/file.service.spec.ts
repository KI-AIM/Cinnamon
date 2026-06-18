import { HttpClient } from '@angular/common/http';
import { FileService } from "./file.service";

describe("FileService", () => {
	let service: FileService;

	beforeEach(() => {
		service = new FileService({} as HttpClient);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
