import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserService } from 'src/app/shared/services/user.service';

@Injectable()
export class XhrInterceptor implements HttpInterceptor {

  constructor(
    private readonly userService: UserService,
  ) { }

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    let header = request.headers.set('X-Requested-With', 'XMLHttpRequest');
    if (this.userService.isAuthenticated()) {
      header = header.set('authorization', 'Basic ' + this.userService.getUser().token);
    }

    const xhr = request.clone({ headers: header });
    return next.handle(xhr);
  }
}
