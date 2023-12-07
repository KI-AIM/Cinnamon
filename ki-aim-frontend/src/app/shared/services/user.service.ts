import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class UserService {

  constructor(
      private readonly http: HttpClient,
  ) { }

    register(request: {
        email: string, password: string, passwordRepeated: string
    }): Observable<any> {
      console.log(request);
        return this.http.post('api/user/register', request);
    }
}
