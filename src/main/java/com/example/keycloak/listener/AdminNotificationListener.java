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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminNotificationListener implements EventListenerProvider {

    private final KeycloakSession session;
    
    private static final String PUSHOVER_TOKEN = System.getenv("PUSHOVER_API_TOKEN");
    private static final String PUSHOVER_USER = System.getenv("PUSHOVER_USER_KEY");
    private static final String PUSHOVER_API_URL = "https://pushover.net";

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

            Map<String, String> formData = new HashMap<>();
            formData.put("token", PUSHOVER_TOKEN);
            formData.put("user", PUSHOVER_USER);
            formData.put("title", "Keycloak Admin Alert");
            formData.put("message", messageText);
            formData.put("priority", "0"); 

            String formBody = formData.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PUSHOVER_API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenAccept(response -> {
                      if (response.statusCode() != 200) {
                          System.err.println("[PUSHOVER ERROR] Failed to send alert. Status code: " + response.statusCode() + " Body: " + response.body());
                      }
                  });

        } catch (Exception e) {
            System.err.println("[PUSHOVER ERROR] Failed to build or send notification message.");
            e.printStackTrace();
        }
    }

    // FIX: Corrected method name from onAdminEvent to onEvent to properly match Keycloak's interface
    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Left empty intentionally: ignoring administrative dashboard changes
    }

    @Override
    public void close() {
        // Cleanup if necessary
    }
}
