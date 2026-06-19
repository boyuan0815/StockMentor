package net.boyuan.stockmentor.auth.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegistrationConflictException extends RuntimeException {
    private final Map<String, String> fields;

    public RegistrationConflictException(boolean emailExists, boolean usernameExists) {
        super(buildMessage(emailExists, usernameExists));
        Map<String, String> nextFields = new LinkedHashMap<>();

        if (emailExists) {
            nextFields.put("email", "Email is already registered.");
        }
        if (usernameExists) {
            nextFields.put("username", "Username is already taken.");
        }

        this.fields = Collections.unmodifiableMap(nextFields);
    }

    public Map<String, String> getFields() {
        return fields;
    }

    private static String buildMessage(boolean emailExists, boolean usernameExists) {
        if (emailExists && usernameExists) {
            return "Email and username are already registered";
        }
        if (emailExists) {
            return "Email is already registered";
        }
        return "Username is already registered";
    }
}
