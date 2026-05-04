package com.scimapp.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.scimapp.backend.security.JsonAuthEntryPoint;
import com.scimapp.backend.security.JwtAuthenticationFilter;
import com.scimapp.backend.security.JwtService;
import com.scimapp.backend.security.ScimAuthProperties;
import com.scimapp.backend.security.ScimBearerAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
		return new JwtAuthenticationFilter(jwtService);
	}

	@Bean
	public ScimBearerAuthenticationFilter scimBearerAuthenticationFilter(
			ScimAuthProperties scimAuthProperties,
			ObjectMapper objectMapper) {
		return new ScimBearerAuthenticationFilter(scimAuthProperties, objectMapper);
	}

	/**
	 * Spring Boot auto-registers {@link jakarta.servlet.Filter} beans on the servlet container.
	 * These filters must run only inside Spring Security's {@link SecurityFilterChain}, not globally.
	 */
	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtServletRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public FilterRegistrationBean<ScimBearerAuthenticationFilter> disableScimServletRegistration(
			ScimBearerAuthenticationFilter filter) {
		FilterRegistrationBean<ScimBearerAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins}") String allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		java.util.List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	@Order(1)
	public SecurityFilterChain scimSecurityFilterChain(
			HttpSecurity http,
			ScimBearerAuthenticationFilter scimBearerAuthenticationFilter,
			JsonAuthEntryPoint jsonAuthEntryPoint) throws Exception {
		return http
				.securityMatcher("/scim/v2/**")
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a.anyRequest().hasRole("SCIM_INTEGRATION"))
				.addFilterBefore(scimBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthEntryPoint))
				.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain appSecurityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			JsonAuthEntryPoint jsonAuthEntryPoint) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/auth/**").permitAll()
						.requestMatchers("/h2-console", "/h2-console/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthEntryPoint))
				.headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
				.build();
	}
}
