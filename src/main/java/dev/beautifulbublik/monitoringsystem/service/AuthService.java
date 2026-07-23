package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.dto.AuthResponse;
import dev.beautifulbublik.monitoringsystem.dto.LoginRequest;
import dev.beautifulbublik.monitoringsystem.dto.RegisterRequest;
import dev.beautifulbublik.monitoringsystem.entity.User;
import dev.beautifulbublik.monitoringsystem.exception.ConflictException;
import dev.beautifulbublik.monitoringsystem.repository.UserRepository;
import dev.beautifulbublik.monitoringsystem.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {


    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with email " + email + " is already registered");
        }

        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password())));
        notificationService.createDefaults(user);

        log.info("Registered user {} (id={})", email, user.getId());
        return issueToken(email);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        log.info("User login {}", email);
        return issueToken(email);
    }

    private AuthResponse issueToken(String email) {
        return AuthResponse.bearer(jwtService.generateToken(email), jwtService.getExpiration().toSeconds());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
