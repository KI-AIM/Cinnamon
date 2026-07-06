export class User {
	constructor(
		public authenticated: boolean,
		public username: string,
		public token: string
	) {}
}
