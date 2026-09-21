package link.srrrg.auth.model;

import java.io.Serializable;
import java.util.Objects;

public class OAuthAccountId implements Serializable {
	private String provider;
	private String providerUserId;

	public OAuthAccountId() {
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof OAuthAccountId id)) {
			return false;
		}
		return Objects.equals(provider, id.provider) && Objects.equals(providerUserId, id.providerUserId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(provider, providerUserId);
	}
}
