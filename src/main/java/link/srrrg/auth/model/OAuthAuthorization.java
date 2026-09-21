package link.srrrg.auth.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "oauth_authorization_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAuthorization {
	@Id
	@Column(name = "token_hash", length = 64)
	private String tokenHash;
	@Column(nullable = false)
	private String state;
	@Column(name = "registration_id", nullable = false)
	private String registrationId;
	@Column(name = "authorization_uri", nullable = false)
	private String authorizationUri;
	@Column(name = "client_id", nullable = false)
	private String clientId;
	@Column(name = "redirect_uri", nullable = false)
	private String redirectUri;
	@Column(nullable = false)
	private String scopes;
	@Column(name = "code_verifier", nullable = false)
	private String codeVerifier;
	@Column(name = "code_challenge", nullable = false)
	private String codeChallenge;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public OAuthAuthorization(String tokenHash, String state, String registrationId, String authorizationUri,
			String clientId, String redirectUri, String scopes, String codeVerifier, String codeChallenge,
			Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.state = state;
		this.registrationId = registrationId;
		this.authorizationUri = authorizationUri;
		this.clientId = clientId;
		this.redirectUri = redirectUri;
		this.scopes = scopes;
		this.codeVerifier = codeVerifier;
		this.codeChallenge = codeChallenge;
		this.expiresAt = expiresAt;
	}
}
