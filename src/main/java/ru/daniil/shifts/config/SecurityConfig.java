package ru.daniil.shifts.config;

import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.web.ApiErrorWriter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Two explicit security boundaries:
 * - /api/mobile/** is stateless and accepts Bearer tokens only;
 * - the web UI and regular API keep JSESSIONID + CSRF, while also accepting
 *   Bearer tokens for mobile clients that use shared endpoints such as tasks.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Persistent browser-login tokens live in the same per-environment database. */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService userDetailsService,
                                                 PersistentTokenRepository persistentTokenRepository,
                                                 @Value("${dutylog.security.remember-me.validity-days:30}") int rememberMeValidityDays,
                                                 @Value("${dutylog.security.remember-me.secure-cookie:false}") boolean rememberMeSecureCookie) {
        int validitySeconds = rememberMeValiditySeconds(rememberMeValidityDays);
        StablePersistentRememberMeServices service = new StablePersistentRememberMeServices(
                "dutylog-stable-remember-me-v1",
                userDetailsService,
                persistentTokenRepository,
                validitySeconds);
        service.setParameter("remember-me");
        service.setCookieName("DUTYLOG_REMEMBER_ME");
        service.setTokenValiditySeconds(validitySeconds);
        service.setUseSecureCookie(rememberMeSecureCookie);
        return service;
    }

    /** Teaches Spring Security to resolve users from the application users table. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
                .map(u -> {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (u.isAdmin()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    return new DutyLogUserPrincipal(
                            u.getUsername(),
                            u.getPasswordHash(),
                            authorities,
                            u.getAuthVersion());
                })
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    /**
     * BearerTokenAuthenticationFilter belongs to Spring Security chains only.
     * Disabling servlet auto-registration prevents it from running twice.
     */
    @Bean
    public FilterRegistrationBean<BearerTokenAuthenticationFilter> bearerFilterRegistration(
            BearerTokenAuthenticationFilter filter) {
        FilterRegistrationBean<BearerTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** WebAccountStateFilter is part of the web Spring Security chain only. */
    @Bean
    public FilterRegistrationBean<WebAccountStateFilter> webAccountStateFilterRegistration(
            WebAccountStateFilter filter) {
        FilterRegistrationBean<WebAccountStateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain mobileFilterChain(HttpSecurity http,
                                                  BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
                                                  SecurityEventLogger securityEvents,
                                                  ApiErrorWriter apiErrors) throws Exception {
        http
                .securityMatcher("/api/mobile/**", "/api/v1/mobile/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/mobile/auth/login",
                                "/api/mobile/auth/refresh",
                                "/api/mobile/auth/logout",
                                "/api/v1/mobile/auth/login",
                                "/api/v1/mobile/auth/register",
                                "/api/v1/mobile/auth/registration-status",
                                "/api/v1/mobile/auth/refresh",
                                "/api/v1/mobile/auth/logout"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> {
                            securityEvents.warn(request, "AUTH_REQUIRED", null, "rejected", "channel=mobile");
                            apiErrors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                    "AUTH_REQUIRED", "Требуется Bearer access token");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            String username = request.getUserPrincipal() == null
                                    ? null
                                    : request.getUserPrincipal().getName();
                            securityEvents.warn(request, "AUTH_ACCESS_DENIED", username, "rejected", "channel=mobile");
                            apiErrors.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                                    "FORBIDDEN", "Недостаточно прав");
                        }))
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http,
                                               BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
                                               WebAccountStateFilter webAccountStateFilter,
                                               SecurityEventLogger securityEvents,
                                               ApiErrorWriter apiErrors,
                                               RememberMeServices rememberMeServices) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        RequestMatcher bearerRequest = request ->
                BearerTokenAuthenticationFilter.hasBearerScheme(request.getHeader("Authorization"));
        // Resolve immediately so the SPA receives XSRF-TOKEN before its first POST.
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers(bearerRequest, new AntPathRequestMatcher("/h2-console/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login.html",
                                "/js/login.js",
                                "/manifest.json",
                                "/service-worker.js",
                                "/icons/**",
                                "/openapi/**",
                                "/api/auth/register",
                                "/api/auth/registration-status",
                                "/h2-console/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/calendar-feed.ics"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/perform_login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, exception) -> {
                            securityEvents.warn(request, "AUTH_LOGIN_FAILED", request.getParameter("username"),
                                    "rejected", "channel=web");
                            response.sendRedirect("/login.html?error");
                        }))
                .rememberMe(remember -> remember
                        .key("dutylog-stable-remember-me-v1")
                        .rememberMeServices(rememberMeServices))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html")
                        .deleteCookies("JSESSIONID", "DUTYLOG_REMEMBER_ME"))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) -> {
                                    securityEvents.warn(request, "AUTH_REQUIRED", null, "rejected", "channel=web-api");
                                    apiErrors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                            "AUTH_REQUIRED", "Требуется авторизация");
                                },
                                new AntPathRequestMatcher("/api/**"))
                        .accessDeniedHandler((request, response, exception) -> {
                            String username = request.getUserPrincipal() == null
                                    ? null
                                    : request.getUserPrincipal().getName();
                            securityEvents.warn(request, "AUTH_ACCESS_DENIED", username, "rejected", "channel=web");
                            apiErrors.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                                    "FORBIDDEN", "Недостаточно прав");
                        }))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(webAccountStateFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    static int rememberMeValiditySeconds(int requestedDays) {
        int safeDays = Math.max(1, Math.min(365, requestedDays));
        return Math.toIntExact(Duration.ofDays(safeDays).toSeconds());
    }
}
