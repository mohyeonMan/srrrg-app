package link.srrrg.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "srrrg.oauth")
public record OAuthClientProperties(Credentials google, Credentials kakao, Credentials github) {
	public record Credentials(String clientId, String clientSecret) {
		public boolean configured() {
			return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
		}

		public boolean partial() {
			return (clientId != null && !clientId.isBlank()) != (clientSecret != null && !clientSecret.isBlank());
		}
	}
}
