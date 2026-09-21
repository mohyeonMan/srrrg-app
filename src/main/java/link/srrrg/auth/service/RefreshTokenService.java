package link.srrrg.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import link.srrrg.auth.model.RefreshToken;
import link.srrrg.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리프레시 토큰 원문은 쿠키에만 두고 DB에는 해시와 회전 상태만 기록한다. */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
	public static final Duration LIFETIME = Duration.ofDays(30);
	private final RefreshTokenRepository repository;

	@Transactional
	public String issue(long userId) {
		return insert(userId, UUID.randomUUID(), Instant.now());
	}

	@Transactional(noRollbackFor = RefreshReuseException.class)
	public Rotated rotate(String raw) {
		RefreshToken current = findForUpdate(raw);
		Instant now = Instant.now();
		if (current.getUsedAt() != null) {
			// 재사용 감지 후 예외가 나더라도 계열 폐기가 롤백되면 안 된다.
			repository.revokeFamily(current.getFamilyId(), now);
			throw new RefreshReuseException();
		}
		if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
			throw new InvalidRefreshException();
		}
		current.use(now);
		String next = insert(current.getUserId(), current.getFamilyId(), now);
		return new Rotated(current.getUserId(), next);
	}

	@Transactional
	public void logout(String raw) {
		if (!validShape(raw)) {
			return;
		}
		RefreshToken current = repository.lockByHash(TokenCodec.hash(raw)).orElse(null);
		if (current != null) {
			repository.revokeFamily(current.getFamilyId(), Instant.now());
		}
	}

	private RefreshToken findForUpdate(String raw) {
		if (!validShape(raw)) {
			throw new InvalidRefreshException();
		}
		return repository.lockByHash(TokenCodec.hash(raw))
				.orElseThrow(InvalidRefreshException::new);
	}

	private String insert(long userId, UUID familyId, Instant now) {
		String raw = "srrrg_rt_" + TokenCodec.random();
		repository.save(new RefreshToken(TokenCodec.hash(raw), userId, familyId, now.plus(LIFETIME)));
		return raw;
	}

	private boolean validShape(String raw) {
		return raw != null && raw.matches("srrrg_rt_[A-Za-z0-9_-]{43}");
	}

	public record Rotated(long userId, String rawToken) {
	}

	public static class InvalidRefreshException extends RuntimeException {
	}

	public static class RefreshReuseException extends RuntimeException {
	}
}
