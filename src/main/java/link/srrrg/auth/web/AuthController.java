package link.srrrg.auth.web;

import link.srrrg.auth.service.JwtIssuer;
import link.srrrg.auth.service.LoginTicketService;
import link.srrrg.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final RefreshTokenService refreshTokens;
	private final JwtIssuer jwtIssuer;
	private final LoginTicketService tickets;

	@PostMapping("/exchange")
	public ResponseEntity<AuthTokens> exchange(@RequestBody ExchangeRequest request) {
		try {
			long userId = tickets.consume(request.ticket());
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
					.body(issueTokens(userId, refreshTokens.issue(userId)));
		} catch (LoginTicketService.InvalidTicketException exception) {
			return ResponseEntity.status(401).build();
		}
	}

	@GetMapping("/me")
	public CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
		return new CurrentUser(Long.parseLong(jwt.getSubject()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthTokens> refresh(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
			String authorization) {
		try {
			RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken(authorization));
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
					.body(issueTokens(rotated.userId(), rotated.rawToken()));
		} catch (RefreshTokenService.InvalidRefreshException | RefreshTokenService.RefreshReuseException exception) {
			return ResponseEntity.status(401).build();
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
			String authorization) {
		String raw = refreshToken(authorization);
		if (raw == null) {
			return ResponseEntity.status(401).build();
		}
		refreshTokens.logout(raw);
		return ResponseEntity.noContent().build();
	}

	private AuthTokens issueTokens(long userId, String refresh) {
		return new AuthTokens("Bearer", jwtIssuer.issue(userId), refresh, JwtIssuer.LIFETIME.toSeconds());
	}

	private String refreshToken(String authorization) {
		return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
				? authorization.substring(7) : null;
	}

	public record ExchangeRequest(String ticket) {
	}

	public record AuthTokens(String tokenType, String accessToken, String refreshToken, long expiresIn) {
	}

	public record CurrentUser(long userId) {
	}
}
