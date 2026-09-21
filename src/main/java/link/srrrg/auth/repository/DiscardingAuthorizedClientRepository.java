package link.srrrg.auth.repository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/** 공급자 토큰은 로그인 직후 사용하지 않으므로 서버 세션이나 파드 메모리에 보관하지 않는다. */
@Component
public class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
	@Override
	public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId,
			Authentication principal, HttpServletRequest request) {
		return null;
	}

	@Override
	public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
			HttpServletRequest request, HttpServletResponse response) {
	}

	@Override
	public void removeAuthorizedClient(String registrationId, Authentication principal,
			HttpServletRequest request, HttpServletResponse response) {
	}
}
