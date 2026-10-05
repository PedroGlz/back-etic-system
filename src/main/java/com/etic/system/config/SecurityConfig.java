package com.etic.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

	// LEGACY_MOBILE_AUTH: contratos Android; no autenticación web por sesión.
	@Bean
	@org.springframework.core.annotation.Order(1)
	SecurityFilterChain mobileSecurityFilterChain(HttpSecurity http) throws Exception {
		return http.securityMatcher("/api/auth/login", "/api/auth/me", "/api/auth/logout", "/api/mobile/**")
			.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED))
			.authorizeHttpRequests(a -> a.anyRequest().permitAll())
			.addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter() {
				@Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res, jakarta.servlet.FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
					if (req.getHeader("Origin") != null) { res.setStatus(403); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"status\":403,\"message\":\"LEGACY_MOBILE_AUTH no disponible para navegadores\"}"); return; }
					chain.doFilter(req,res);
				}
			}, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).build();
	}

	@Bean
	@org.springframework.core.annotation.Order(2)
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${LICENSE_CONTROL_JWT_SECRET:}") String secret) throws Exception {
		return http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
			.exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"status\":401,\"message\":\"Se requiere JWT ETIC_ONLINE válido\"}");})
				.accessDeniedHandler((req,res,ex) -> res.setStatus(403)))
			.authorizeHttpRequests(a -> a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health").permitAll().requestMatchers("/api/**").authenticated().anyRequest().denyAll())
			.addFilterBefore(new com.etic.system.auth.security.WebJwtFilter(secret), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
		@Value("${ETIC_FRONTEND_ORIGINS:http://localhost:4200,http://localhost:4400}") String origins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
