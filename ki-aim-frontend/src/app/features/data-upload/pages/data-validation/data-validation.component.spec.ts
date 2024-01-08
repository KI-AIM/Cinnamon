import { ComponentFixture, TestBed } from "@angular/core/testing";

import { DataValidationComponent } from "./data-validation.component";

describe("DataValidationComponent", () => {
	let component: DataValidationComponent;
	let fixture: ComponentFixture<DataValidationComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [DataValidationComponent],
		}).compileComponents();

		fixture = TestBed.createComponent(DataValidationComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it("should create", () => {
		expect(component).toBeTruthy();
	});
});
