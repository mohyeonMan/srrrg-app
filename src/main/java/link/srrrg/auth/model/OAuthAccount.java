package link.srrrg.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "oauth_accounts")
@IdClass(OAuthAccountId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccount {
	@Id
	private String provider;

	@Id
	@Column(name = "provider_user_id")
	private String providerUserId;

	@Column(name = "user_id", nullable = false)
	private Long userId;
}
