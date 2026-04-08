package com.karim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.karim.service.impl.CustomUserDetailsService;
import com.karim.util.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final CustomUserDetailsService userDetailsService;
	private final JwtAuthFilter jwtAuthFilter;

	public SecurityConfig(CustomUserDetailsService userDetailsService, JwtAuthFilter jwtAuthFilter) {
		this.userDetailsService = userDetailsService;
		this.jwtAuthFilter = jwtAuthFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		http
				// Disable CSRF — REST API with stateless JWT doesn't need it
				.csrf(AbstractHttpConfigurer::disable)

				// No session — every request must carry its own token
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {

					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setContentType("application/json");

					response.getWriter().write("""
							    {
							      "success": false,
							      "error": "Unauthorized - Token missing or invalid",
							      "statusCode": 401
							    }
							""");
				}).accessDeniedHandler((request, response, accessDeniedException) -> {

					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					response.setContentType("application/json");

					response.getWriter().write("""
							    {
							      "success": false,
							      "error": "Forbidden - You don’t have permission",
							      "statusCode": 403
							    }
							""");
				}))

				// Route-level authorization
				.authorizeHttpRequests(auth -> auth

						// ── SWAGGER & OPENAPI ROUTES ────────────────────────
						.requestMatchers("/v3/api-docs/**", "/v2/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
								"/swagger-resources/**", "/webjars/**")
						.permitAll()

						// ── PUBLIC AUTH ROUTES ──────────────────────────────
						.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/refresh-token").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/otp/email/verify").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/otp/email/send").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/otp/email/send/").permitAll()
						.requestMatchers(
							    "/register.html",
							    "/login.html",
							    "/forgot-password.html",
							    "/activate.html",
							    "/reset-password.html",
							    "/products.html",
							    "/delivery-dashboard.html",
							    "/admin.html"
							).permitAll()

						// ── PUBLIC READ ROUTES ──────────────────────────────
						.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories",
								"/api/categories/**")
						.permitAll()						
						// ── ADMIN-ONLY ROUTES ───────────────────────────────
						// User management
						.requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/users/*/restore").hasRole("ADMIN")
						// Product & category write
						.requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/categories/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
						// Order admin
						.requestMatchers("/api/orders/admin/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/orders/*/confirm", "/api/orders/*/processing",
								"/api/orders/*/dispatch", "/api/orders/*/out-for-delivery", "/api/orders/*/deliver")
						.hasRole("ADMIN")
						// Delivery management
						.requestMatchers(HttpMethod.POST, "/api/delivery/agents").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/delivery/agents/*/verify").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/api/delivery/agents/*/suspend").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/delivery/agents/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/delivery/assignments").hasRole("ADMIN")
						// Pricing config
						.requestMatchers("/api/delivery/pricing-config/**").hasRole("ADMIN")
						// Earnings & payouts
						.requestMatchers(HttpMethod.POST, "/api/earnings/*/approve", "/api/earnings/*/dispute",
								"/api/earnings/*/incentive", "/api/earnings/*/penalty", "/api/earnings/payouts",
								"/api/earnings/payouts/*/process", "/api/earnings/payouts/*/success",
								"/api/earnings/payouts/*/fail")
						.hasRole("ADMIN")
						// Returns admin
						.requestMatchers("/api/returns/admin/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/returns/*/approve", "/api/returns/*/reject",
								"/api/returns/*/schedule-pickup", "/api/returns/*/mark-picked-up",
								"/api/returns/*/initiate-refund", "/api/returns/*/complete")
						.hasRole("ADMIN")
						// Payments admin
						.requestMatchers(HttpMethod.POST, "/api/payments/*/success", "/api/payments/*/fail",
								"/api/payments/*/refund")
						.hasRole("ADMIN").requestMatchers(HttpMethod.POST, "/api/payments/invoices/*/regenerate")
						.hasRole("ADMIN")

						// ── DELIVERY AGENT ROUTES ───────────────────────────
						.requestMatchers("/api/delivery/agents/me/**").hasAnyRole("DELIVERY", "ADMIN")
						.requestMatchers("/api/delivery/assignments/agent/me/**").hasAnyRole("DELIVERY", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/delivery/assignments/*/accept",
								"/api/delivery/assignments/*/reject", "/api/delivery/assignments/*/pick-up",
								"/api/delivery/assignments/*/in-transit", "/api/delivery/assignments/*/deliver",
								"/api/delivery/assignments/*/fail")
						.hasAnyRole("DELIVERY", "ADMIN").requestMatchers("/api/delivery/rides/**")
						.hasAnyRole("DELIVERY", "ADMIN").requestMatchers("/api/tracking/ping/**")
						.hasAnyRole("DELIVERY", "ADMIN").requestMatchers("/api/earnings/me/**")
						.hasAnyRole("DELIVERY", "ADMIN").requestMatchers("/api/earnings/payouts/agent/me/**")
						.hasAnyRole("DELIVERY", "ADMIN")

						// ── EVERYTHING ELSE — authenticated users only ──────
						.anyRequest().authenticated())

				// Wire our DaoAuthenticationProvider
				.authenticationProvider(authenticationProvider())

				// Add JwtAuthFilter BEFORE Spring's default username/password filter
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public DaoAuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

		authProvider.setUserDetailsService(userDetailsService); // ✅ FIX
		authProvider.setPasswordEncoder(passwordEncoder());

		return authProvider;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
		return authConfig.getAuthenticationManager();
	}
}