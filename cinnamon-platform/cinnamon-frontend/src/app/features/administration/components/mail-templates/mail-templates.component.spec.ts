import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { NotificationService } from '@core/services/notification.service';
import { EmailTemplate, EmailTemplateList } from '@shared/model/admin-settings';
import { AdminService } from '@shared/services/admin.service';
import { ErrorHandlingService } from '@shared/services/error-handling.service';
import { UserService } from '@shared/services/user.service';
import { of } from 'rxjs';
import { MailTemplatesComponent } from './mail-templates.component';

/**
 * The template is only configured for one of the two supported languages.
 */
const templateList: EmailTemplateList = {
    languages: [
        {name: 'ENGLISH', displayName: 'English'},
        {name: 'GERMAN', displayName: 'German'},
    ],
    templates: [
        {
            id: 1,
            name: 'Registration confirmation',
            items: [{language: 'ENGLISH', subject: 'Welcome', body: 'Hello!'}],
        },
    ],
};

/**
 * Providers stubbing everything the component talks to.
 */
function createProviders(): unknown[] {
    return [
        {provide: AdminService, useValue: {getEmailTemplates: () => of(templateList)}},
        {provide: ErrorHandlingService, useValue: {addError: () => undefined}},
        {provide: MatDialog, useValue: {}},
        {provide: NotificationService, useValue: {addNotification: () => undefined}},
        {provide: UserService, useValue: {getUser: () => ({userInfo: {username: 'admin'}})}},
    ];
}

describe('MailTemplatesComponent', () => {
    let component: MailTemplatesComponent;
    let fixture: ComponentFixture<MailTemplatesComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [MailTemplatesComponent],
            providers: [FormBuilder, ...createProviders()],
            schemas: [NO_ERRORS_SCHEMA],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(MailTemplatesComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should only create a tab for the languages of the template', () => {
        const items = component['form'].get('items') as FormGroup;

        expect(Object.keys(items.controls)).toEqual(['ENGLISH']);
        expect(component['addedLanguages'].map(language => language.name)).toEqual(['ENGLISH']);
        expect(component['selectedTabIndex']).toBe(0);
    });

    it('should offer the languages that have not been added yet', () => {
        expect(component['availableLanguages'].map(language => language.name)).toEqual(['GERMAN']);
    });

    it('should apply the content of the selected template', () => {
        expect(component['form'].get('name')!.value).toBe('Registration confirmation');
        expect(component['languageGroup']('ENGLISH').value).toEqual({subject: 'Welcome', body: 'Hello!'});
    });

    it('should add an empty tab for the selected language and select it', () => {
        component['addLanguage']('GERMAN');

        expect(component['addedLanguages'].map(language => language.name)).toEqual(['ENGLISH', 'GERMAN']);
        expect(component['availableLanguages']).toEqual([]);
        expect(component['languageGroup']('GERMAN').value).toEqual({subject: '', body: ''});
        expect(component['selectedTabIndex']).toBe(1);
        expect(component['form'].dirty).toBeTrue();
        expect(component['addLanguageControl'].value).toBeNull();
    });

    it('should not add a language twice', () => {
        component['addLanguage']('ENGLISH');

        expect(component['addedLanguages'].map(language => language.name)).toEqual(['ENGLISH']);
        expect(component['languageGroup']('ENGLISH').value).toEqual({subject: 'Welcome', body: 'Hello!'});
    });

    it('should be invalid while an added language is incomplete', () => {
        component['addLanguage']('GERMAN');

        expect(component['form'].valid).toBeFalse();

        component['languageGroup']('GERMAN').patchValue({subject: 'Willkommen', body: 'Hallo!'});

        expect(component['form'].valid).toBeTrue();
    });

    it('should keep the selection inside the remaining tabs when a language is removed', () => {
        component['addLanguage']('GERMAN');
        expect(component['selectedTabIndex']).toBe(1);

        component['removeLanguage']('GERMAN');

        expect(component['addedLanguages'].map(language => language.name)).toEqual(['ENGLISH']);
        expect(component['availableLanguages'].map(language => language.name)).toEqual(['GERMAN']);
        expect(component['selectedTabIndex']).toBe(0);
    });

    it('should be invalid if no language is added', () => {
        component['removeLanguage']('ENGLISH');

        expect(component['form'].hasError('noLanguageConfigured')).toBeTrue();
        expect(component['form'].valid).toBeFalse();
    });

    it('should send every added language', () => {
        component['addLanguage']('GERMAN');
        component['languageGroup']('GERMAN').patchValue({subject: 'Willkommen', body: 'Hallo!'});

        const template: EmailTemplate = component['createTemplate']();

        expect(template.id).toBe(1);
        expect(template.name).toBe('Registration confirmation');
        expect(template.items).toEqual([
            jasmine.objectContaining({language: 'ENGLISH', subject: 'Welcome', body: 'Hello!'}),
            jasmine.objectContaining({language: 'GERMAN', subject: 'Willkommen', body: 'Hallo!'}),
        ]);
    });

    it('should not send a removed language', () => {
        component['addLanguage']('GERMAN');
        component['languageGroup']('GERMAN').patchValue({subject: 'Willkommen', body: 'Hallo!'});
        component['removeLanguage']('ENGLISH');

        expect(component['createTemplate']().items).toEqual([
            jasmine.objectContaining({language: 'GERMAN', subject: 'Willkommen', body: 'Hallo!'}),
        ]);
    });

    it('should start a new template with the first supported language', () => {
        component['addTemplate']();

        expect(component['creatingTemplate']).toBeTrue();
        expect(component['addedLanguages'].map(language => language.name)).toEqual(['ENGLISH']);
        expect(component['languageGroup']('ENGLISH').value).toEqual({subject: '', body: ''});
        expect(component['form'].pristine).toBeTrue();
    });

});

/**
 * Renders the component with the real Material and form directives to verify that the content of a language is bound
 * correctly inside its tab.
 */
describe('MailTemplatesComponent rendering', () => {
    let component: MailTemplatesComponent;
    let fixture: ComponentFixture<MailTemplatesComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [MailTemplatesComponent],
            imports: [
                MatFormFieldModule,
                MatInputModule,
                MatSelectModule,
                MatTabsModule,
                NoopAnimationsModule,
                ReactiveFormsModule,
            ],
            providers: createProviders(),
            schemas: [NO_ERRORS_SCHEMA],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(MailTemplatesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should render one tab per added language', () => {
        expect(tabLabels()).toEqual(['English']);

        component['addLanguage']('GERMAN');
        fixture.detectChanges();

        expect(tabLabels()).toEqual(['English', 'German']);
    });

    it('should render the select for adding a language next to the tab labels', () => {
        const addLanguage = fixture.nativeElement.querySelector('.mail-templates-add-language');

        expect(addLanguage).not.toBeNull();
        expect(addLanguage.closest('.mail-templates-tabs')).not.toBeNull();

        // The select is only useful as long as a language can be added.
        component['addLanguage']('GERMAN');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.mail-templates-add-language')).toBeNull();
    });

    it('should bind the content of every tab to its own language', () => {
        expect(subjectInputs().map(input => input.value)).toEqual(['Welcome']);

        component['addLanguage']('GERMAN');
        fixture.detectChanges();

        // The tab of the added language has its own, still empty content.
        const inputs = subjectInputs();
        expect(inputs.map(input => input.value)).toEqual(['Welcome', '']);

        inputs[1].value = 'Willkommen';
        inputs[1].dispatchEvent(new Event('input'));

        expect(component['languageGroup']('GERMAN').value.subject).toBe('Willkommen');
        expect(component['languageGroup']('ENGLISH').value.subject).toBe('Welcome');
    });

    function tabLabels(): string[] {
        return Array.from(fixture.nativeElement.querySelectorAll('[role="tab"]'))
                    .map(tab => (tab as HTMLElement).textContent!.trim());
    }

    /**
     * The subject inputs of all rendered tabs, in the order of the tabs.
     */
    function subjectInputs(): HTMLInputElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll('input[formControlName="subject"]'));
    }

});
