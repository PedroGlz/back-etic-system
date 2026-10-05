package com.etic.system.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validador web: no emite JWT ni acepta identidad de HttpSession. */
public class WebJwtFilter extends OncePerRequestFilter {
    private final byte[] secret;
    private final ObjectMapper json = new ObjectMapper();
    public WebJwtFilter(String value) {
        if (value == null || value.length() < 32) throw new IllegalStateException("Configurar LICENSE_CONTROL_JWT_SECRET con al menos 32 caracteres");
        secret = value.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return "/actuator/health".equals(request.getServletPath()); }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null) { chain.doFilter(req, res); return; }
        JsonNode claims;
        try {
            if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
            String[] parts = header.substring(7).split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            var decoder = Base64.getUrlDecoder();
            if (!"HS256".equals(json.readTree(decoder.decode(parts[0])).path("alg").asText())) throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII)), decoder.decode(parts[2]))) throw new IllegalArgumentException();
            claims = json.readTree(decoder.decode(parts[1]));
            if (!claims.path("exp").isIntegralNumber() || !claims.path("exp").canConvertToLong()
                    || claims.path("exp").asLong() <= Instant.now().getEpochSecond()
                    || claims.path("sub").asText().isBlank() || claims.path("username").asText().isBlank()) throw new IllegalArgumentException();
            strings(claims.path("roles")); strings(claims.path("permissions"));
        } catch (Exception e) { reject(res, 401, "Token inválido o expirado"); return; }
        if (!"ETIC_ONLINE".equals(claims.path("system").asText())) { reject(res, 403, "Token para otro sistema"); return; }
        List<String> roles = strings(claims.path("roles")), permissions = strings(claims.path("permissions"));
        if (roles.isEmpty()) { reject(res, 403, "Se requiere un rol de ETIC_ONLINE"); return; }
        var user = new WebIdentity(claims.path("sub").asText(), claims.path("username").asText(),
                text(claims,"firstName"), text(claims,"lastName"), text(claims,"email"), roles, permissions, claims.path("systemAdmin").asBoolean(false));
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_"+r)));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
        SecurityContextHolder.setContext(context);
        chain.doFilter(req, res);
    }
    private List<String> strings(JsonNode node) {
        if (!node.isArray()) throw new IllegalArgumentException();
        List<String> result = new ArrayList<>();
        node.forEach(n -> { if (!n.isTextual() || n.asText().isBlank()) throw new IllegalArgumentException(); result.add(n.asText()); });
        return List.copyOf(result);
    }
    private String text(JsonNode claims,String name) { return claims.path(name).isTextual() ? claims.path(name).asText() : null; }
    private void reject(HttpServletResponse res,int status,String message) throws IOException {
        SecurityContextHolder.clearContext();res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
        json.writeValue(res.getWriter(), java.util.Map.of("status",status,"message",message));
    }
}
