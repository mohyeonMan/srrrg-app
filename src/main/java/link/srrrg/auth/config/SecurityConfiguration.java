package link.srrrg.auth.config;

import link.srrrg.auth.repository.DatabaseAuthorizationRequestRepository;
import link.srrrg.auth.repository.DiscardingAuthorizedClientRepository;
import link.srrrg.auth.web.OAuthLoginFailureHandler;
import link.srrrg.auth.web.OAuthLoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			ClientRegistrationRepository registrations,
			DatabaseAuthorizationRequestRepository requests,
			DiscardingAuthorizedClientRepository authorizedClients,
			OAuthLoginSuccessHandler successHandler,
			OAuthLoginFailureHandler failureHandler) throws Exception {
		DefaultOAuth2AuthorizationRequestResolver resolver =
				new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

		DefaultBearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();
		return http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/oauth2/authorization/**",
								"/login/oauth2/code/**", "/api/auth/exchange",
								"/api/auth/refresh", "/api/auth/logout").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll())
				.oauth2Login(oauth -> oauth
						.authorizedClientRepository(authorizedClients)
						.authorizationEndpoint(endpoint -> endpoint
								.authorizationRequestResolver(resolver)
								.authorizationRequestRepository(requests))
						.successHandler(successHandler)
						.failureHandler(failureHandler))
				.oauth2ResourceServer(resource -> resource
						.bearerTokenResolver(request -> {
							String path = request.getRequestURI().substring(request.getContextPath().length());
							// 갱신·로그아웃의 Bearer 값은 JWT가 아닌 리프레시 토큰이므로 각 API가 검증한다.
							if (!path.startsWith("/api/") || path.equals("/api/auth/refresh")
									|| path.equals("/api/auth/logout") || path.equals("/api/auth/exchange")) {
								return null;
							}
							return bearerTokens.resolve(request);
						})
						.jwt(Customizer.withDefaults()))
				.build();
	}
}
