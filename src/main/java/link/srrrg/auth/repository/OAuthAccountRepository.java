package link.srrrg.auth.repository;

import java.util.Optional;

import link.srrrg.auth.model.OAuthAccount;
import link.srrrg.auth.model.OAuthAccountId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, OAuthAccountId> {
	@Query("select account.userId from OAuthAccount account where account.provider = :provider and account.providerUserId = :providerUserId")
	Optional<Long> findUserId(String provider, String providerUserId);
}
