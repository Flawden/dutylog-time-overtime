package ru.daniil.shifts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import ru.daniil.shifts.repo.UserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Гибридная авторизация:
 * - веб остаётся на классической сессии JSESSIONID;
 * - Android/PWA API может использовать Authorization: Bearer <accessToken>.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Учит Spring Security искать пользователей в нашей таблице users. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
                .map(u -> {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (u.isAdmin()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    return User.withUsername(u.getUsername())
                            .password(u.getPasswordHash())
                            .authorities(authorities)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) throws Exception {
        // CSRF для SPA: токен кладётся в cookie XSRF-TOKEN (доступную JS),
        // фронтенд возвращает его заголовком X-XSRF-TOKEN на каждом
        // изменяющем запросе (это делает jfetch в app.js).
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // null = резолвить токен сразу на каждом запросе, чтобы cookie
        // гарантированно появилась ещё до первого POST (стандартный SPA-рецепт).
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(csrfHandler)
                // Мобильный API stateless на Bearer-токенах — CSRF-атака на него
                // невозможна (браузер не подставит Authorization сам), поэтому исключаем.
                // h2-console — дев-инструмент со своими формами.
                .ignoringRequestMatchers("/api/mobile/**", "/h2-console/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/login.html",
                        "/manifest.json",
                        "/service-worker.js",
                        "/icons/**",
                        "/api/auth/register",
                        "/api/mobile/auth/login",
                        "/api/mobile/auth/refresh",
                        "/api/mobile/auth/logout",
                        "/h2-console/**",
                        "/actuator/health",
                        "/actuator/health/**"
                ).permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/perform_login") // сюда POST-ит форма входа
                .defaultSuccessUrl("/", true)
                .failureUrl("/login.html?error"))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html"))
            // Для fetch-запросов к API без сессии отдаём 401,
            // а не редирект на страницу входа (фронт сам перекинет).
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                (req, res, e) -> res.sendError(401),
                new AntPathRequestMatcher("/api/**")))
            // h2-console живёт в iframe — разрешаем со своего origin
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
