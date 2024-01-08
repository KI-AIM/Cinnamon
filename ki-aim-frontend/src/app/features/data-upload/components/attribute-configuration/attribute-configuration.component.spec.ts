import { ComponentFixture, TestBed } from "@angular/core/testing";

import { AttributeConfigurationComponent } from "./attribute-configuration.component";

describe("AttributeConfigurationComponent", () => {
	let component: AttributeConfigurationComponent;
	let fixture: ComponentFixture<AttributeConfigurationComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [AttributeConfigurationComponent],
		}).compileComponents();

		fixture = TestBed.createComponent(AttributeConfigurationComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it("should create", () => {
		expect(component).toBeTruthy();
	});
});
