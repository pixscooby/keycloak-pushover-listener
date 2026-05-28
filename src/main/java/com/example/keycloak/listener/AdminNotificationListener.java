package com.example.keycloak.listener;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class AdminNotificationListener implements EventListenerProvider {

    private final KeycloakSession session;

    public AdminNotificationListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        // Listen specifically for user registration events
        if (EventType.REGISTER.equals(event.getType())) {
            String realmId = event.getRealmId();
            String userId = event.getUserId();
            
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);
            
            sendAdminNotification(user, realm);
        }
    }

    private void sendAdminNotification(UserModel user, RealmModel realm) {
        String username = user.getUsername();
        String email = user.getEmail();
        
        // OPTION A: Print to logs (Good for log forwarders like Splunk/Datadog)
        System.out.printf("[ADMIN NOTIFICATION] New user created in realm %s. Username: %s, Email: %s%n", 
            realm.getName(), username, email);

        // OPTION B: Call an external HTTP Webhook (Teams, Slack, or custom API)
        // You can use standard Java HttpClient here to POST this data payload.
    }

    @Override
    public void onAdminEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Ignored for standard user registration
    }

    @Override
    public void close() {
        // Cleanup if necessary
    }
}
