import { HttpClient } from '@angular/common/http';
import { Router } from "@angular/router";
import { NotificationService } from "@core/services/notification.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { ProjectService } from "@shared/services/project.service";
import { UserService } from "@shared/services/user.service";
import { FileService } from "./file.service";

describe("FileService", () => {
	let service: FileService;

	beforeEach(() => {
        const httpClient = {} as HttpClient; // Mock HttpClient
        const notificationService = new NotificationService();
        const router = {} as Router; // Mock Router

        service = new FileService(
            new ErrorHandlingService(notificationService, new UserService(httpClient, notificationService, router)),
            httpClient,
            new ProjectService(httpClient)
        );
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});
});
