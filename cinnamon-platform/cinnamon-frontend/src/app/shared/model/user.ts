export class User {
	constructor(
		public authenticated: boolean,
        public userInfo: UserInfo,
		public token: string,
	) {}
}

export class UserInfo {
    username: string = "";
    roles: UserRole[] = [];
}

export enum UserRole {
    ROLE_ADMIN = "ROLE_ADMIN",
    ROLE_USER = "ROLE_USER",
}
