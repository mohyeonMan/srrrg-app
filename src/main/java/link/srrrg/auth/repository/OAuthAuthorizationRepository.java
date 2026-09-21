package link.srrrg.auth.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import link.srrrg.auth.model.OAuthAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OAuthAuthorizationRepository extends JpaRepository<OAuthAuthorization, String> {
	Optional<OAuthAuthorization> findByTokenHashAndStateAndExpiresAtAfter(
			String tokenHash, String state, Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from OAuthAuthorization request where request.tokenHash = :tokenHash and request.state = :state and request.expiresAt > :now")
	Optional<OAuthAuthorization> lockValid(String tokenHash, String state, Instant now);

	@Modifying
	@Query("delete from OAuthAuthorization request where request.expiresAt <= :now")
	int deleteExpired(Instant now);
}
