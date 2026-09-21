package link.srrrg.auth;

import link.srrrg.auth.service.JwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"srrrg.oauth.google.client-id=test-client",
		"srrrg.oauth.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class AuthSecurityTests {
	@Autowired MockMvc mvc;
	@Autowired JwtIssuer issuer;

	@Test
	void currentUserRequiresBearerHeader() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").cookie(new MockCookie("srrrg_access", issuer.issue(42))))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue(42)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(42));
	}

	@Test
	void refreshRequiresRefreshBearerToken() throws Exception {
		mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRequiresRefreshBearerToken() throws Exception {
		mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
	}

	@Test
	void csrfEndpointIsRemoved() throws Exception {
		mvc.perform(get("/api/auth/csrf")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue(42)))
				.andExpect(status().isNotFound());
	}

	@Test
	void onlyConfiguredFrontendOriginCanUseApiCors() throws Exception {
		mvc.perform(options("/api/auth/exchange")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
		mvc.perform(options("/api/auth/exchange")
				.header(HttpHeaders.ORIGIN, "https://untrusted.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}

	@Test
	void oauthStartPersistsPkceRequestAndRedirectsToProvider() throws Exception {
		var response = mvc.perform(get("/oauth2/authorization/google").header("Host", "untrusted.example"))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse();
		assertTrue(response.getRedirectedUrl().startsWith("https://accounts.google.com/o/oauth2/v2/auth"));
		assertTrue(response.getRedirectedUrl().contains("code_challenge="));
		assertTrue(response.getRedirectedUrl().contains("redirect_uri=http://localhost:8080/login/oauth2/code/google"));
		assertTrue(response.getHeaders("Set-Cookie").stream()
				.anyMatch(value -> value.startsWith("srrrg_oauth_")));
	}
}
