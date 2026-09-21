package link.srrrg.auth.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.config.AuthProperties;
import link.srrrg.auth.service.LoginTicketService;
import link.srrrg.auth.service.OAuthAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
	private final OAuthAccountService accounts;
	private final LoginTicketService tickets;
	private final AuthProperties properties;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
		long userId = accounts.resolve(oauth.getAuthorizedClientRegistrationId(), oauth.getName());
		response.setHeader("Cache-Control", "no-store");
		response.sendRedirect(properties.frontendPopupSuccessUri(tickets.issue(userId)));
	}
}
