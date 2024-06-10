import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { plainToInstance } from 'class-transformer';
import { ErrorResponse } from '../model/error-response';

@Injectable({
  providedIn: 'root'
})
export class ErrorMessageService {

  constructor() { }

  /**
   * Extracts the error message from an HttpErrorResponse.
   * @param error The error response.
   * @returns The error message.
   */
  public extractErrorMessage(error: HttpErrorResponse): string {
		if (typeof error.error === 'string') {
        return error.error;
		} else {
			const errorResponse = plainToInstance(ErrorResponse, error.error);
      return errorResponse.errorMessage;
		}
  }

  /**
   * Converts a HttpErrorResponse to an error message formatted in HTML.
   * @param error The error response to convert.
   * @returns An HTML error message.
   */
	public convertResponseToMessage(error: HttpErrorResponse | null) {
		if (error === null) {
			return "";
		}

		if (error.status === 504) {
			return this.wrapErrorMessage("The API at " + error.url + " could not be reached");
		}

		let errorMessage = '';

		if (typeof error.error === 'string') {
			errorMessage = this.wrapErrorMessage(error.error);
		} else {
			const errorResponse = plainToInstance(ErrorResponse, error.error);

			if (errorResponse.errorCode === '3-2-1') {
				errorMessage = this.wrapErrorMessage(JSON.stringify(errorResponse.errorDetails, null, 2));
			} else {
				errorMessage = this.wrapErrorMessage(errorResponse.errorMessage);
			}
		}

		return errorMessage;
	}

	private wrapErrorMessage(errorMessage: string) {
		return "<div class='pre-wrapper'><pre>" + errorMessage + "</pre></div>";
	}

}
