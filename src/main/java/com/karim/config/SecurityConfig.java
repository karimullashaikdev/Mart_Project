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
	        .csrf(AbstractHttpConfigurer::disable)

	        .sessionManagement(session ->
	            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

	        .exceptionHandling(ex -> ex
	            .authenticationEntryPoint((request, response, authException) -> {
	                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	                response.setContentType("application/json");
	                response.getWriter().write("""
	                    {
	                      "success": false,
	                      "error": "Unauthorized - Token missing or invalid",
	                      "statusCode": 401
	                    }
	                """);
	            })
	            .accessDeniedHandler((request, response, accessDeniedException) -> {
	                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
	                response.setContentType("application/json");
	                response.getWriter().write("""
	                    {
	                      "success": false,
	                      "error": "Forbidden - You don’t have permission",
	                      "statusCode": 403
	                    }
	                """);
	            })
	        )

	        .authorizeHttpRequests(auth -> auth

	            // Swagger
	            .requestMatchers(
	                "/v3/api-docs/**",
	                "/swagger-ui/**",
	                "/swagger-ui.html",
	                "/swagger-resources/**",
	                "/webjars/**"
	            ).permitAll()

	            // Static HTML pages
	         // Public pages
	            .requestMatchers(
	                "/register.html",
	                "/login.html",
	                "/forgot-password.html",
	                "/activate.html",
	                "/reset-password.html",
	                "/products.html"
	            ).permitAll()

	            // Protected HTML pages
	            .requestMatchers("/delivery.html")
	                .hasRole("DELIVERY")

	            .requestMatchers("/admin.html")
	                .hasRole("ADMIN")

	            .requestMatchers("/my-orders.html")
	                .authenticated()

	            // Static resources
	            .requestMatchers(
	                "/css/**",
	                "/js/**",
	                "/images/**",
	                "/icons/**",
	                "/assets/**",
	                "/favicon.ico"
	            ).permitAll()

	            // Auth APIs
	            .requestMatchers("/api/auth/**").permitAll()

	            // Public APIs
	            .requestMatchers(HttpMethod.POST, "/api/images/upload").permitAll()
	            .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()

	            // User self APIs
	            .requestMatchers(HttpMethod.GET, "/api/users/me/**").authenticated()
	            .requestMatchers(HttpMethod.POST, "/api/users/me/**").authenticated()
	            .requestMatchers(HttpMethod.PUT, "/api/users/me/**").authenticated()
	            .requestMatchers(HttpMethod.PATCH, "/api/users/me/**").authenticated()
	            .requestMatchers(HttpMethod.DELETE, "/api/users/me/**").authenticated()

	            // Payments
	            .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
	            .requestMatchers("/api/payments/**").authenticated()

	            // Admin APIs
	            .requestMatchers("/api/admin/**").hasRole("ADMIN")

	            // Everything else
	            .anyRequest().authenticated()
	        )

	        .authenticationProvider(authenticationProvider())
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