package com.jobtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtracker.config.AppProperties;
import com.jobtracker.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-characters-long";

    private static AppProperties props(String secret) {
        return new AppProperties(null, new AppProperties.Jwt(secret, 24), null, null, null, null);
    }

    private static User user() {
        User u = new User();
        u.setId(42L);
        u.setEmail("jane@example.com");
        return u;
    }

    @Test
    void roundTripReturnsTheSameUser() {
        JwtService jwt = new JwtService(props(SECRET), Clock.systemUTC());

        CurrentUser parsed = jwt.parse(jwt.generateToken(user())).orElseThrow();

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("jane@example.com");
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        String token = new JwtService(props(SECRET), past).generateToken(user());

        assertThat(new JwtService(props(SECRET), Clock.systemUTC()).parse(token)).isEmpty();
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        JwtService jwt = new JwtService(props(SECRET), Clock.systemUTC());
        String token = jwt.generateToken(user());
        String foreign = new JwtService(props("another-secret-that-is-also-32-chars-long!!"), Clock.systemUTC())
                .generateToken(user());

        int middle = token.indexOf('.') + 5; // inside the payload section
        char original = token.charAt(middle);
        String tampered = token.substring(0, middle) + (original == 'A' ? 'B' : 'A') + token.substring(middle + 1);

        assertThat(jwt.parse(tampered)).isEmpty();
        assertThat(jwt.parse(foreign)).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesToStartWithAWeakSecret() {
        assertThatThrownBy(() -> new JwtService(props("short"), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtService(props(""), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
