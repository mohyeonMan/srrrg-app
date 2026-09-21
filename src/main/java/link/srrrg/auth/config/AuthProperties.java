package link.srrrg.auth.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "srrrg.auth")
public record AuthProperties(String baseUrl, String frontendUrl, String jwtKeyBase64) {
	public String callbackUri() {
		return baseUrl.replaceAll("/+$", "") + "/login/oauth2/code/{registrationId}";
	}

	public boolean secureCookies() {
		return "https".equalsIgnoreCase(URI.create(baseUrl).getScheme());
	}

	public String frontendPopupSuccessUri(String ticket) {
		if (frontendUrl == null || frontendUrl.isBlank()) {
			throw new IllegalStateException("SRRRG_FRONTEND_URL을 설정해야 합니다.");
		}
		return frontendUrl.replaceAll("/+$", "") + "/auth/popup#ticket=" + ticket;
	}

	public String frontendPopupFailureUri() {
		if (frontendUrl == null || frontendUrl.isBlank()) {
			throw new IllegalStateException("SRRRG_FRONTEND_URL을 설정해야 합니다.");
		}
		return frontendUrl.replaceAll("/+$", "") + "/auth/popup#error=oauth_failed";
	}
}
