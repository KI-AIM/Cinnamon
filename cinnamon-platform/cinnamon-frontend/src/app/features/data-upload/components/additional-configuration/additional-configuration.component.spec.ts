import { ComponentFixture, TestBed } from "@angular/core/testing";

import { AdditionalConfigurationComponent } from "./additional-configuration.component";

describe("AdditionalConfigurationComponent", () => {
	let component: AdditionalConfigurationComponent;
	let fixture: ComponentFixture<AdditionalConfigurationComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [AdditionalConfigurationComponent],
		}).compileComponents();

		fixture = TestBed.createComponent(AdditionalConfigurationComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it("should create", () => {
		expect(component).toBeTruthy();
	});
});
