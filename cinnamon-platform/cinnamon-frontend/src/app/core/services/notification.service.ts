import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from "rxjs";

/**
 * Service for managing notifications in the UI.
 * The notifications are not stored in the backend.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class NotificationService {

    /**
     * Timeout in milliseconds after which the latest notification is cleared.
     */
    private readonly NOTIFICATION_TIMEOUT = 5000;

    /**
     * Delay in milliseconds after which the latest notification is shown.
     * Has to match the animation delay in the notification component.
     */
    private readonly ANIMATION_DELAY = 300;

    private notifications: BehaviorSubject<AppNotification[]> = new BehaviorSubject<AppNotification[]>([]);

    private latestNotification: BehaviorSubject<AppNotification | null> = new BehaviorSubject<AppNotification | null>(null);
    private latestNotificationTimeoutId: number | null = null;
    private animationDelayTimeoutId: number | null = null;

    public constructor() {
    }

    public addNotification(notification: AppNotification) {
        // Add the new notification to the top of the list
        const currentNotifications = this.notifications.getValue();
        notification.id = currentNotifications.length;
        this.notifications.next([...currentNotifications, notification]);

        // Clear any pending timeouts to prevent conflicts
        if (this.latestNotificationTimeoutId) {
            clearTimeout(this.latestNotificationTimeoutId);
            this.latestNotificationTimeoutId = null;
        }
        if (this.animationDelayTimeoutId) {
            clearTimeout(this.animationDelayTimeoutId);
            this.animationDelayTimeoutId = null;
        }

        const hasActiveNotification = !!this.latestNotification.getValue();
        const delay = hasActiveNotification ? this.ANIMATION_DELAY : 0;

        if (hasActiveNotification) {
            // Trigger the leave animation by clearing the current notification
            this.latestNotification.next(null);
        }

        // Wait for the leave animation to finish before showing the new one
        // @ts-ignore
        this.animationDelayTimeoutId = setTimeout(() => {
            this.latestNotification.next(notification);

            // @ts-ignore
            this.latestNotificationTimeoutId = setTimeout(() => {
                this.clearLatestNotification();
            }, this.NOTIFICATION_TIMEOUT);
        }, delay);
    }

    /**
     * Clears the latest notification.
     */
    public clearLatestNotification() {
        this.latestNotification.next(null);

        if (this.latestNotificationTimeoutId) {
            clearTimeout(this.latestNotificationTimeoutId);
            this.latestNotificationTimeoutId = null;
        }
    }

    /**
     * Notifies about all notifications.
     */
    public notifications$(): Observable<AppNotification[]> {
        return this.notifications.asObservable();
    }

    /**
     * Notifies about the latest notification.
     * Sends null if the latest notification was cleared.
     */
    public latestNotification$(): Observable<AppNotification | null> {
        return this.latestNotification.asObservable();
    }
}

/**
 * Data for showing a notification in the UI.
 */
export class AppNotification {
    constructor(
        public message: string,
        public type: 'success' | 'warn' | 'failure') {
    }

    public time: Date = new Date();
    public id: number;
}
