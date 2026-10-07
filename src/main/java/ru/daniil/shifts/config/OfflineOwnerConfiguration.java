package ru.daniil.shifts.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Bind queued browser mutations to the authenticated owner, including cookie changes in another tab. */
@Configuration
public class OfflineOwnerConfiguration implements WebMvcConfigurer {
    private final ru.daniil.shifts.service.CurrentUserService users;
    public OfflineOwnerConfiguration(ru.daniil.shifts.service.CurrentUserService users) { this.users = users; }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OfflineOwnerInterceptor(users)).addPathPatterns("/api/**");
    }
}
