package ru.daniil.shifts.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Session principal with a durable authentication version.
 *
 * Password and role changes increment {@code authVersion} in the users table.
 * A web session carrying an older version is rejected on its next request.
 */
public final class DutyLogUserPrincipal implements UserDetails {
    private final String username;
    private final String passwordHash;
    private final List<? extends GrantedAuthority> authorities;
    private final long authVersion;

    public DutyLogUserPrincipal(String username,
                                String passwordHash,
                                Collection<? extends GrantedAuthority> authorities,
                                long authVersion) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.authorities = List.copyOf(authorities);
        this.authVersion = Math.max(0L, authVersion);
    }

    public long getAuthVersion() {
        return authVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
