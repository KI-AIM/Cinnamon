import { CanActivateFn } from '@angular/router';

export const projectAccessGuard: CanActivateFn = (route, state) => {
    return true;
};
