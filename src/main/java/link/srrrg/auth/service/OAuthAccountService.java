package link.srrrg.auth.service;

import java.util.Set;

import jakarta.persistence.EntityManager;
import link.srrrg.auth.model.User;
import link.srrrg.auth.repository.OAuthAccountRepository;
import link.srrrg.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 없이 공급자와 공급자 사용자 ID만으로 계정을 식별한다. */
@Service
@RequiredArgsConstructor
public class OAuthAccountService {
	private static final Set<String> PROVIDERS = Set.of("google", "kakao", "github");
	private final OAuthAccountRepository accounts;
	private final UserRepository users;
	private final EntityManager entityManager;

	@Transactional
	public long resolve(String provider, String providerUserId) {
		if (!PROVIDERS.contains(provider) || providerUserId == null || providerUserId.isBlank()
				|| providerUserId.length() > 255) {
			throw new IllegalArgumentException("OAuth 사용자 식별자가 유효하지 않습니다.");
		}
		Long existing = accounts.findUserId(provider, providerUserId).orElse(null);
		if (existing != null) {
			return existing;
		}
		long newUserId = users.saveAndFlush(User.create()).getId();
		// 계정 생성의 유일성은 DB 제약으로 보장한다. JPA의 조회 후 저장만으로는 동시 로그인을 막을 수 없다.
		var inserted = entityManager.createNativeQuery("""
				INSERT INTO oauth_accounts (user_id, provider, provider_user_id)
				VALUES (:userId, :provider, :providerUserId)
				ON CONFLICT (provider, provider_user_id) DO NOTHING
				RETURNING user_id
				""")
				.setParameter("userId", newUserId)
				.setParameter("provider", provider)
				.setParameter("providerUserId", providerUserId)
				.getResultList();
		if (!inserted.isEmpty()) {
			return ((Number) inserted.getFirst()).longValue();
		}
		// 다른 파드가 같은 계정을 먼저 만들었으면 경쟁 중 만든 빈 사용자만 제거한다.
		users.deleteById(newUserId);
		Long winner = accounts.findUserId(provider, providerUserId).orElse(null);
		if (winner == null) {
			throw new IllegalStateException("OAuth 계정 생성 결과를 찾을 수 없습니다.");
		}
		return winner;
	}
}
