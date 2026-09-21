package link.srrrg.auth.config;

import java.net.URI;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class JwtConfiguration {
	@Bean
	SecretKey jwtSecretKey(AuthProperties properties) {
		if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
			throw new IllegalStateException("SRRRG_BASE_URL을 설정해야 합니다.");
		}
		URI baseUrl = URI.create(properties.baseUrl());
		if (baseUrl.getHost() == null || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
				|| baseUrl.getFragment() != null || !(baseUrl.getPath().isEmpty() || "/".equals(baseUrl.getPath()))) {
			throw new IllegalStateException("SRRRG_BASE_URL은 경로 없는 서비스 주소여야 합니다.");
		}
		boolean localHttp = "http".equalsIgnoreCase(baseUrl.getScheme())
				&& ("localhost".equalsIgnoreCase(baseUrl.getHost()) || "127.0.0.1".equals(baseUrl.getHost()));
		if (!"https".equalsIgnoreCase(baseUrl.getScheme()) && !localHttp) {
			throw new IllegalStateException("인증 API는 HTTPS 또는 로컬 HTTP에서만 사용할 수 있습니다.");
		}
		if (properties.frontendUrl() != null && !properties.frontendUrl().isBlank()) {
			URI frontend = URI.create(properties.frontendUrl());
			boolean localFrontend = "http".equalsIgnoreCase(frontend.getScheme())
					&& ("localhost".equalsIgnoreCase(frontend.getHost())
							|| "127.0.0.1".equals(frontend.getHost()));
			if (frontend.getHost() == null || frontend.getUserInfo() != null || frontend.getQuery() != null
					|| frontend.getFragment() != null
					|| !(frontend.getPath().isEmpty() || "/".equals(frontend.getPath()))
					|| (!"https".equalsIgnoreCase(frontend.getScheme()) && !localFrontend)) {
				throw new IllegalStateException("SRRRG_FRONTEND_URL은 HTTPS 또는 로컬 HTTP의 출처여야 합니다.");
			}
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(properties.jwtKeyBase64());
			if (bytes.length < 32) {
				throw new IllegalStateException("JWT 키는 256비트 이상이어야 합니다.");
			}
			return new SecretKeySpec(bytes, "HmacSHA256");
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new IllegalStateException("SRRRG_JWT_KEY_BASE64에 Base64 키를 설정해야 합니다.", exception);
		}
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer("srrrg"),
				jwt -> "access".equals(jwt.getClaimAsString("token_type"))
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
		return decoder;
	}
}
