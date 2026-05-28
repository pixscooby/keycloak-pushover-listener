package com.example.keycloak.listener;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class AdminNotificationListener implements EventListenerProvider {

    private final KeycloakSession session;
    
    private static final String PUSHOVER_TOKEN = System.getenv("PUSHOVER_API_TOKEN");
    private static final String PUSHOVER_USER = System.getenv("PUSHOVER_USER_KEY");
    // Standard secure endpoint
    private static final String PUSHOVER_API_URL = "https://api.pushover.net";

    public AdminNotificationListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (EventType.REGISTER.equals(event.getType())) {
            String userId = event.getUserId();
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);
            
            if (user != null) {
                sendPushoverNotification(user, realm);
            }
        }
    }

    private void sendPushoverNotification(UserModel user, RealmModel realm) {
        if (PUSHOVER_TOKEN == null || PUSHOVER_USER == null) {
            System.err.println("[PUSHOVER ERROR] Missing PUSHOVER_API_TOKEN or PUSHOVER_USER_KEY environment variables.");
            return;
        }

        try {
            String messageText = String.format("New user registered.\nUsername: %s\nEmail: %s\nRealm: %s", 
                    user.getUsername(), user.getEmail(), realm.getName());

            // Build query parameters directly to prevent proxies from stripping a POST body
            String queryParams = String.format("token=%s&user=%s&title=%s&message=%s&priority=0",
                    URLEncoder.encode(PUSHOVER_TOKEN, StandardCharsets.UTF_8),
                    URLEncoder.encode(PUSHOVER_USER, StandardCharsets.UTF_8),
                    URLEncoder.encode("Keycloak Admin Alert", StandardCharsets.UTF_8),
                    URLEncoder.encode(messageText, StandardCharsets.UTF_8)
            );

            // Create the full URI target string
            URI fullUri = URI.create(PUSHOVER_API_URL + "?" + queryParams);

            // Enforce HTTP/1.1 explicitly to pass through old enterprise corporate proxies safely
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(fullUri)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody()) // Sending values via URI parameters safely
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenAccept(response -> {
                      if (response.statusCode() != 200) {
                          System.err.println("[PUSHOVER ERROR] Failed to send alert. Status code: " + response.statusCode() + " Body: " + response.body());
                      } else {
                          System.out.println("[PUSHOVER SUCCESS] Notification delivered successfully.");
                      }
                  });

        } catch (Exception e) {
            System.err.println("[PUSHOVER ERROR] Failed to build or send notification message.");
            e.printStackTrace();
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {}

    @Override
    public void close() {}
}
