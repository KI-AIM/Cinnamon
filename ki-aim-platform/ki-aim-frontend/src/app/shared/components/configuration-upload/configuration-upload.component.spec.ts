import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurationUploadComponent } from './configuration-upload.component';

describe('ConfigurationUploadComponent', () => {
  let component: ConfigurationUploadComponent;
  let fixture: ComponentFixture<ConfigurationUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurationUploadComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigurationUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
