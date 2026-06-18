import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { UserService } from "./user.service";

describe("UserService", () => {
	let service: UserService;

	beforeEach(() => {
		service = new UserService(
			{} as HttpClient,
			{ addNotification: jasmine.createSpy('addNotification') } as any,
			{ navigate: jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true)) } as unknown as Router,
		);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
