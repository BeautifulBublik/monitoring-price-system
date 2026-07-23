package dev.beautifulbublik.monitoringsystem.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * {@link org.springframework.security.core.userdetails.UserDetails} with an added {@code userId}.
 * <p>
 * Thanks to this, controllers get the owner's id straight from {@code Authentication}
 * and do not hit the DB for the user on every request.
 */
@Getter
public class AuthenticatedUser extends User {

    private final Long userId;

    public AuthenticatedUser(Long userId,
                             String email,
                             String passwordHash,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.userId = userId;
    }

}
