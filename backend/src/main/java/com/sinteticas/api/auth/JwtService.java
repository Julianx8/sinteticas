package com.sinteticas.api.auth;

import com.sinteticas.api.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Emite los tokens de acceso. La validación la hace Spring Security con el {@code JwtDecoder}
 * de {@link SecurityConfig}.
 */
@Service
public class JwtService {

    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties.Jwt jwtProperties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, SecurityProperties securityProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = securityProperties.security().jwt();
        this.clock = clock;
    }

    public String issueToken(User user) {
        Instant now = clock.instant();
        List<String> roles = user.getRoleNames().stream().map(Enum::name).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiration()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim(ROLES_CLAIM, roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration expiration() {
        return jwtProperties.expiration();
    }

}
