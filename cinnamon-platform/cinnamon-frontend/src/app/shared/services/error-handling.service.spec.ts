import { ErrorHandlingService } from './error-handling.service';

describe('ErrorHandlingService', () => {
  let service: ErrorHandlingService;

  beforeEach(() => {
    service = new ErrorHandlingService(
      { addNotification: jasmine.createSpy('addNotification') } as any,
      {
        getUser: () => ({ email: 'test@example.com' }),
        logout: jasmine.createSpy('logout'),
      } as any,
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
