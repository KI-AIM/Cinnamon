import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { throwError } from "rxjs";
import { AdminService } from "./admin.service";

describe("AdminService", () => {

    it("should be created", () => {
        expect(new AdminService({} as HttpClient)).toBeTruthy();
    });

    it("should emit null if the mail settings are not configured", (done) => {
        const service = new AdminService(createHttpClientFailingWith(404));

        service.getMailSettings().subscribe(value => {
            expect(value).toBeNull();
            done();
        });
    });

    it("should forward errors other than not found", (done) => {
        const service = new AdminService(createHttpClientFailingWith(500));

        service.getMailSettings().subscribe({
            error: (error: HttpErrorResponse) => {
                expect(error.status).toBe(500);
                done();
            },
        });
    });

    function createHttpClientFailingWith(status: number): HttpClient {
        return {
            get: () => throwError(() => new HttpErrorResponse({status: status})),
        } as unknown as HttpClient;
    }

});
