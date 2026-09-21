package link.srrrg.auth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresOAuthFlowTests.FakeProvider.class)
@EnabledIfEnvironmentVariable(named = "SRRRG_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresOAuthFlowTests {
	@Autowired MockMvc mvc;
	private static HttpServer server;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("SRRRG_TEST_DATABASE_URL"));
		registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SRRRG_TEST_DATABASE_USER", "srrrg"));
		registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SRRRG_TEST_DATABASE_PASSWORD", "srrrg-local-password"));
	}

	@AfterAll
	static void stopProvider() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	@Transactional
	void callbackIssuesSingleUseTicketAndHeaderTokens() throws Exception {
		OAuthStart start = startOAuth();
		var callback = mvc.perform(get("/login/oauth2/code/github")
					.param("code", "test-code")
					.param("state", start.state())
					.cookie(start.cookie()))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse();
		assertTrue(callback.getRedirectedUrl().startsWith("http://localhost:5173/auth/popup#ticket="));
		assertTrue(callback.getHeaders("Set-Cookie").stream()
				.noneMatch(value -> value.startsWith("srrrg_access=") || value.startsWith("srrrg_refresh=")));
		String ticket = URI.create(callback.getRedirectedUrl()).getFragment().substring("ticket=".length());
		String body = "{\"ticket\":\"" + ticket + "\"}";
		var exchange = mvc.perform(post("/api/auth/exchange").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn().getResponse();
		String access = JsonPath.read(exchange.getContentAsString(), "$.accessToken");
		String refresh = JsonPath.read(exchange.getContentAsString(), "$.refreshToken");
		mvc.perform(post("/api/auth/exchange").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").isNumber());
		var rotated = mvc.perform(post("/api/auth/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
				.andExpect(status().isOk()).andReturn().getResponse();
		String nextRefresh = JsonPath.read(rotated.getContentAsString(), "$.refreshToken");
		mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + nextRefresh))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + nextRefresh))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Transactional
	void callbackFailureReturnsPopupSafeError() throws Exception {
		OAuthStart start = startOAuth();
		var callback = mvc.perform(get("/login/oauth2/code/github")
					.param("error", "access_denied")
					.param("error_description", "provider detail must not be exposed")
					.param("state", start.state())
					.cookie(start.cookie()))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse();

		assertEquals("http://localhost:5173/auth/popup#error=oauth_failed", callback.getRedirectedUrl());
	}

	private OAuthStart startOAuth() throws Exception {
		var start = mvc.perform(get("/oauth2/authorization/github"))
				.andExpect(status().is3xxRedirection()).andReturn().getResponse();
		String state = Arrays.stream(URI.create(start.getRedirectedUrl()).getRawQuery().split("&"))
				.filter(part -> part.startsWith("state="))
				.map(part -> URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8))
				.findFirst().orElseThrow();
		String oauthCookie = start.getHeaders("Set-Cookie").stream()
				.filter(value -> value.startsWith("srrrg_oauth_"))
				.findFirst().orElseThrow().split(";", 2)[0];
		String[] oauthPair = oauthCookie.split("=", 2);
		return new OAuthStart(state, new MockCookie(oauthPair[0], oauthPair[1]));
	}

	private record OAuthStart(String state, MockCookie cookie) {
	}

	@TestConfiguration
	static class FakeProvider {
		@Bean
		@Primary
		ClientRegistrationRepository fakeClientRegistrationRepository() throws IOException {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/token", exchange -> reply(exchange,
					"{\"access_token\":\"test-provider-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
			server.createContext("/user", exchange -> reply(exchange, "{\"id\":777}"));
			server.start();
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			ClientRegistration registration = ClientRegistration.withRegistrationId("github")
					.clientId("test-client")
					.clientSecret("test-secret")
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
					.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
					.redirectUri("http://localhost:8080/login/oauth2/code/{registrationId}")
					.authorizationUri(base + "/authorize")
					.tokenUri(base + "/token")
					.userInfoUri(base + "/user")
					.userNameAttributeName("id")
					.clientName("Test GitHub")
					.build();
			return id -> "github".equals(id) ? registration : null;
		}

		private void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var stream = exchange.getResponseBody()) {
				stream.write(bytes);
			}
		}
	}
}
