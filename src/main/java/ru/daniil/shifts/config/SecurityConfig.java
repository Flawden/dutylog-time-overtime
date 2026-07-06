package ru.daniil.shifts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import ru.daniil.shifts.repo.UserRepository;

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
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) throws Exception {
        http
            // CSRF отключён сознательно: без него fetch-запросы (PUT/POST/DELETE)
            // работают без танцев с токенами. Перед выкладкой в интернет —
            // включить обратно и пробрасывать токен, см. README.
            .csrf(csrf -> csrf.disable())
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
