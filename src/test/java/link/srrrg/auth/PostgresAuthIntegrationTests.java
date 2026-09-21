package link.srrrg.auth;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;
import link.srrrg.auth.model.User;
import link.srrrg.auth.repository.DatabaseAuthorizationRequestRepository;
import link.srrrg.auth.repository.UserRepository;
import link.srrrg.auth.service.LoginTicketService;
import link.srrrg.auth.service.OAuthAccountService;
import link.srrrg.auth.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SRRRG_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresAuthIntegrationTests {
	@Autowired OAuthAccountService accounts;
	@Autowired RefreshTokenService refreshTokens;
	@Autowired DatabaseAuthorizationRequestRepository requests;
	@Autowired UserRepository users;
	@Autowired LoginTicketService tickets;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("SRRRG_TEST_DATABASE_URL"));
		registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SRRRG_TEST_DATABASE_USER", "srrrg"));
		registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SRRRG_TEST_DATABASE_PASSWORD", "srrrg-local-password"));
	}

	@Test
	@Transactional
	void accountAndRefreshRotationStayConsistent() {
		String providerId = "test-" + java.util.UUID.randomUUID();
		long userId = accounts.resolve("github", providerId);
		assertEquals(userId, accounts.resolve("github", providerId));
		String first = refreshTokens.issue(userId);
		String second = refreshTokens.rotate(first).rawToken();
		assertThrows(RefreshTokenService.RefreshReuseException.class, () -> refreshTokens.rotate(first));
		assertThrows(RefreshTokenService.InvalidRefreshException.class, () -> refreshTokens.rotate(second));
	}

	@Test
	void reusedRefreshRevokesFamilyAfterTransactionCommits() {
		long userId = accounts.resolve("github", "reuse-test-" + java.util.UUID.randomUUID());
		try {
			String first = refreshTokens.issue(userId);
			String second = refreshTokens.rotate(first).rawToken();
			assertThrows(RefreshTokenService.RefreshReuseException.class, () -> refreshTokens.rotate(first));
			// 예외로 메서드가 끝나도 계열 폐기가 DB에 커밋되어야 한다.
			assertThrows(RefreshTokenService.InvalidRefreshException.class, () -> refreshTokens.rotate(second));
		} finally {
			users.deleteById(userId);
		}
	}

	@Test
	void loginTicketCanBeConsumedOnlyOnceAcrossTransactions() throws Exception {
		long userId = users.saveAndFlush(User.create()).getId();
		try {
			String ticket = tickets.issue(userId);
			CountDownLatch start = new CountDownLatch(1);
			try (var workers = Executors.newFixedThreadPool(2)) {
				java.util.concurrent.Callable<Boolean> consume = () -> {
					start.await();
					try {
						tickets.consume(ticket);
						return true;
					} catch (LoginTicketService.InvalidTicketException exception) {
						return false;
					}
				};
				var first = workers.submit(consume);
				var second = workers.submit(consume);
				start.countDown();
				int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
						+ (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
				assertEquals(1, successes);
			}
		} finally {
			users.deleteById(userId);
		}
	}

	@Test
	@Transactional
	void oauthRequestIsBoundToCookieAndConsumedOnce() {
		OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri("https://example.org/oauth/authorize")
				.clientId("test-client")
				.redirectUri("http://localhost:8080/login/oauth2/code/github")
				.state("state-" + java.util.UUID.randomUUID())
				.additionalParameters(params -> params.put("code_challenge", "challenge"))
				.attributes(attrs -> {
					attrs.put("registration_id", "github");
					attrs.put("code_verifier", "verifier");
				})
				.build();
		MockHttpServletRequest start = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		requests.saveAuthorizationRequest(authorization, start, response);
		String cookieHeader = response.getHeader("Set-Cookie");
		assertNotNull(cookieHeader);
		String[] pair = cookieHeader.split(";", 2)[0].split("=", 2);
		MockHttpServletRequest callback = new MockHttpServletRequest();
		callback.setParameter("state", authorization.getState());
		callback.setCookies(new Cookie(pair[0], pair[1]));
		assertNotNull(requests.loadAuthorizationRequest(callback));
		assertNotNull(requests.removeAuthorizationRequest(callback, new MockHttpServletResponse()));
		assertNull(requests.removeAuthorizationRequest(callback, new MockHttpServletResponse()));
	}
}
