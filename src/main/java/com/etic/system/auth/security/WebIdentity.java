package com.etic.system.auth.security;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record WebIdentity(String id, String username, String firstName, String lastName,
        String email, List<String> roles, List<String> permissions, boolean systemAdmin) implements java.security.Principal {
    @Override public String getName() { return id; }
    public static WebIdentity current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof WebIdentity user))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere JWT ETIC_ONLINE");
        return user;
    }
    public Map<String,Object> profile() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("username", username); result.put("firstName", firstName);
        result.put("lastName", lastName); result.put("email", email); result.put("system", "ETIC_ONLINE");
        result.put("roles", roles); result.put("permissions", permissions); result.put("systemAdmin", systemAdmin);
        return result;
    }
}
