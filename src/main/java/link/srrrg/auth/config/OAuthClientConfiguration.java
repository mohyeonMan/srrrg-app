package link.srrrg.auth.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
@EnableConfigurationProperties(OAuthClientProperties.class)
class OAuthClientConfiguration {
	@Bean
	ClientRegistrationRepository clientRegistrationRepository(OAuthClientProperties properties,
			AuthProperties authProperties) {
		Map<String, ClientRegistration> registrations = new HashMap<>();
		check("google", properties.google());
		check("kakao", properties.kakao());
		check("github", properties.github());
		if (registrationsConfigured(properties) && (authProperties.frontendUrl() == null
				|| authProperties.frontendUrl().isBlank())) {
			throw new IllegalStateException("OAuth 로그인을 사용하려면 SRRRG_FRONTEND_URL을 설정해야 합니다.");
		}
		if (properties.google() != null && properties.google().configured()) {
			registrations.put("google", ClientRegistration.withRegistrationId("google")
					.clientId(properties.google().clientId())
					.clientSecret(properties.google().clientSecret())
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.redirectUri(authProperties.callbackUri())
					.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
					.tokenUri("https://oauth2.googleapis.com/token")
					.userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
					.userNameAttributeName("sub")
					.scope("profile")
					.clientName("Google")
					.build());
		}
		if (properties.github() != null && properties.github().configured()) {
			registrations.put("github", ClientRegistration.withRegistrationId("github")
					.clientId(properties.github().clientId())
					.clientSecret(properties.github().clientSecret())
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.redirectUri(authProperties.callbackUri())
					.authorizationUri("https://github.com/login/oauth/authorize")
					.tokenUri("https://github.com/login/oauth/access_token")
					.userInfoUri("https://api.github.com/user")
					.userNameAttributeName("id")
					.clientName("GitHub")
					.build());
		}
		if (properties.kakao() != null && properties.kakao().configured()) {
			registrations.put("kakao", ClientRegistration.withRegistrationId("kakao")
					.clientId(properties.kakao().clientId())
					.clientSecret(properties.kakao().clientSecret())
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.redirectUri(authProperties.callbackUri())
					.authorizationUri("https://kauth.kakao.com/oauth/authorize")
					.tokenUri("https://kauth.kakao.com/oauth/token")
					.userInfoUri("https://kapi.kakao.com/v2/user/me")
					.userNameAttributeName("id")
					.clientName("Kakao")
					.build());
		}
		// 자격 증명이 없는 로컬 개발 환경에서도 앱을 시작할 수 있다. 해당 공급자 경로는 사용할 수 없다.
		return registrations::get;
	}

	private void check(String name, OAuthClientProperties.Credentials credentials) {
		if (credentials != null && credentials.partial()) {
			throw new IllegalStateException(name + " OAuth client ID와 secret을 함께 설정해야 합니다.");
		}
	}

	private boolean registrationsConfigured(OAuthClientProperties properties) {
		return properties.google() != null && properties.google().configured()
				|| properties.kakao() != null && properties.kakao().configured()
				|| properties.github() != null && properties.github().configured();
	}
}
