package link.srrrg.auth.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {
	private final AuthProperties properties;

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		// 공급자의 상세 오류는 브라우저 주소에 노출하지 않고 프런트가 처리할 고정 결과만 전달한다.
		response.setHeader("Cache-Control", "no-store");
		response.sendRedirect(properties.frontendPopupFailureUri());
	}
}
