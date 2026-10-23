/**
 * Pages of the administration interface.
 * Adding a page to {@link AdminPageConfiguration} is enough to make it available: the navigation renders an entry
 * for it automatically and the corresponding route has to be registered in the routing module.
 */
export enum AdminPage {
    GENERAL = "GENERAL",
    USERS = "USERS",
    MAIL = "MAIL",
}

export interface AdminPageDefinition {
    /**
     * Route the navigation entry links to, relative to the application root.
     */
    path: string;

    /**
     * ID of the navigation link. Used for external selenium scripts, do not remove or change existing ones.
     */
    id: string;

    /**
     * Label shown in the navigation.
     */
    text: string;

    enum: AdminPage;

    /**
     * Position of the entry in the navigation.
     */
    index: number;
}

export const AdminPageConfiguration: Record<AdminPage, AdminPageDefinition> = {
    GENERAL: {
        path: "/admin/general",
        id: "navLinkAdminGeneral",
        text: "General",
        enum: AdminPage.GENERAL,
        index: 0,
    },
    USERS: {
        path: "/admin/users",
        id: "navLinkAdminUsers",
        text: "Users",
        enum: AdminPage.USERS,
        index: 1,
    },
    MAIL: {
        path: "/admin/mail",
        id: "navLinkAdminMail",
        text: "Mail Settings",
        enum: AdminPage.MAIL,
        index: 2,
    },
};
