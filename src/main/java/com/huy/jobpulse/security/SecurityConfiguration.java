package com.huy.jobpulse.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain metricsFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/prometheus")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("MONITOR"))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> oidcUsers) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/auth/session", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/jobs", "/api/v1/jobs/*",
                        "/api/v1/analytics/jobs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**", "/api/v1/ingestion-targets/**",
                        "/api/v1/ingestions/**", "/api/v1/ingestion-requests/**")
                        .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/jobs").hasRole("ADMIN")
                .requestMatchers("/api/v1/saved-searches/**", "/api/v1/alerts/**")
                        .authenticated()
                .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                .anyRequest().denyAll());
        http.oauth2Login(oauth -> oauth
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUsers))
                .defaultSuccessUrl("/jobs", true));
        http.csrf(Customizer.withDefaults());
        http.logout(logout -> logout.logoutUrl("/logout")
                .logoutSuccessUrl("/jobs")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                request -> request.getRequestURI().startsWith("/api/")
                        || request.getRequestURI().startsWith("/actuator/")));
        return http.build();
    }

    @Bean
    UserDetailsService monitorUser(
            @Value("${jobpulse.security.monitor-password}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername("metrics")
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
                .roles("MONITOR").build());
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUsers(
            @Value("${jobpulse.security.admin-emails:}") String configuredEmails) {
        Set<String> adminEmails = Arrays.stream(configuredEmails.split(","))
                .map(email -> email.strip().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser user = delegate.loadUser(request);
            if (!Boolean.TRUE.equals(user.getClaimAsBoolean("email_verified"))) {
                throw new OAuth2AuthenticationException(new OAuth2Error("unverified_email"));
            }
            Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if (user.getEmail() != null && adminEmails.contains(
                    user.getEmail().toLowerCase(Locale.ROOT))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), "sub");
        };
    }
}
