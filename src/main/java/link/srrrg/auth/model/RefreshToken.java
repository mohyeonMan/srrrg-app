package link.srrrg.auth.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
	@Id
	@Column(name = "token_hash", length = 64)
	private String tokenHash;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(name = "family_id", nullable = false)
	private UUID familyId;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	@Column(name = "used_at")
	private Instant usedAt;
	@Column(name = "revoked_at")
	private Instant revokedAt;

	public RefreshToken(String tokenHash, long userId, UUID familyId, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
	}

	public void use(Instant now) {
		usedAt = now;
	}
}
