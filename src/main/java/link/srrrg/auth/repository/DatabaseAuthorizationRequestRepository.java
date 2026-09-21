package link.srrrg.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.config.AuthProperties;
import link.srrrg.auth.model.OAuthAuthorization;
import link.srrrg.auth.service.TokenCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 세션을 쓰지 않고 OAuth state와 PKCE 검증 값을 파드 사이에 공유한다. */
@Component
@RequiredArgsConstructor
public class DatabaseAuthorizationRequestRepository
		implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
	private static final Duration LIFETIME = Duration.ofMinutes(10);
	private static final String COOKIE_PREFIX = "srrrg_oauth_";
	private final OAuthAuthorizationRepository repository;
	private final AuthProperties properties;

	@Override
	@Transactional
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
			HttpServletRequest request, HttpServletResponse response) {
		if (authorizationRequest == null) {
			clear(request.getParameter("state"), response);
			return;
		}
		String verifier = authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER);
		String challenge = (String) authorizationRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE);
		String registrationId = authorizationRequest.getAttribute("registration_id");
		if (verifier == null || challenge == null || registrationId == null) {
			throw new IllegalStateException("OAuth 인가 요청에 PKCE 정보가 없습니다.");
		}
		// 로그인 시도 때 만료 행을 정리해 파드별 청소 작업을 만들지 않는다.
		repository.deleteExpired(Instant.now());
		String raw = TokenCodec.random();
		repository.saveAndFlush(new OAuthAuthorization(TokenCodec.hash(raw), authorizationRequest.getState(),
				registrationId, authorizationRequest.getAuthorizationUri(), authorizationRequest.getClientId(),
				authorizationRequest.getRedirectUri(), String.join(" ", authorizationRequest.getScopes()),
				verifier, challenge, Instant.now().plus(LIFETIME)));
		cookie(response, name(authorizationRequest.getState()), raw, LIFETIME);
	}

	@Override
	@Transactional(readOnly = true)
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return find(request, false);
	}

	@Override
	@Transactional
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
			HttpServletResponse response) {
		OAuth2AuthorizationRequest result = find(request, true);
		clear(request.getParameter("state"), response);
		return result;
	}

	private OAuth2AuthorizationRequest find(HttpServletRequest request, boolean consume) {
		String state = request.getParameter("state");
		String cookieName = name(state);
		if (cookieName == null || request.getCookies() == null) {
			return null;
		}
		String raw = Arrays.stream(request.getCookies())
				.filter(cookie -> cookieName.equals(cookie.getName()))
				.map(Cookie::getValue).findFirst().orElse(null);
		if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) {
			return null;
		}
		OAuthAuthorization stored = consume
				? repository.lockValid(TokenCodec.hash(raw), state, Instant.now()).orElse(null)
				: repository.findByTokenHashAndStateAndExpiresAtAfter(TokenCodec.hash(raw), state, Instant.now())
						.orElse(null);
		if (stored == null) {
			return null;
		}
		if (consume) {
			// 잠금 아래 삭제를 즉시 반영해 다른 파드가 같은 요청을 소비하지 못하게 한다.
			repository.delete(stored);
			repository.flush();
		}
		return OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri(stored.getAuthorizationUri())
				.clientId(stored.getClientId())
				.redirectUri(stored.getRedirectUri())
				.scopes(stored.getScopes().isBlank() ? Set.of() : Set.of(stored.getScopes().split(" ")))
				.state(state)
				.additionalParameters(params -> {
					params.put(PkceParameterNames.CODE_CHALLENGE, stored.getCodeChallenge());
					params.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");
				})
				.attributes(attrs -> {
					attrs.put("registration_id", stored.getRegistrationId());
					attrs.put(PkceParameterNames.CODE_VERIFIER, stored.getCodeVerifier());
				})
				.build();
	}

	private String name(String state) {
		return state == null || state.isBlank() ? null : COOKIE_PREFIX + TokenCodec.hash(state).substring(0, 16);
	}

	private void clear(String state, HttpServletResponse response) {
		String cookieName = name(state);
		if (cookieName != null) {
			cookie(response, cookieName, "", Duration.ZERO);
		}
	}

	private void cookie(HttpServletResponse response, String name, String value, Duration age) {
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(properties.secureCookies())
				.sameSite("Lax")
				.path("/")
				.maxAge(age)
				.build().toString());
	}
}
