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
    ROLE_API = "ROLE_API",
    ROLE_MONITORING = "ROLE_MONITORING",
    ROLE_USER = "ROLE_USER",
}

export enum UserInvitationStatus {
    NOT_SENT = "NOT_SENT",
    PENDING = "PENDING",
    ACCEPTED = "ACCEPTED",
    REVOKED = "REVOKED",

    // Only available in the frontend, not in the backend.
    NOT_CREATED = "NOT_CREATED",
}

export class UserInvitationInfo {
    id: string | null;
    status: UserInvitationStatus;
    email: string | null;
    userRoles: UserRole[];
    emailTemplateItemId: number | null;
    emailCustomSubject: string | null;
    emailCustomBody: string | null;
    createdAt: Date | null;
    lastSentAt: Date | null;
    expiresAt: Date | null;
    acceptedAt: Date | null;
    revokedAt: Date | null;
    invitedBy: string | null;
    acceptedBy: string | null;
}
