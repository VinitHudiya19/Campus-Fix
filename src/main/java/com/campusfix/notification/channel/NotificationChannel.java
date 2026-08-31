package com.campusfix.notification.channel;

import com.campusfix.notification.NotificationType;
import com.campusfix.request.ServiceRequest;
import com.campusfix.user.User;

/**
 * One way of telling somebody something.
 *
 * <p>Spring injects every implementation as a list, so adding a channel means
 * adding a class — nothing that dispatches notifications has to change. The
 * in-app channel is always present; the email one only registers when SMTP is
 * configured, so the application behaves identically with no mail server.
 */
public interface NotificationChannel {

    void deliver(User recipient, ServiceRequest request, NotificationType type, String message);

    /** For the startup log, so it is obvious which channels are live. */
    String describe();
}
