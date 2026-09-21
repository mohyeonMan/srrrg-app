package link.srrrg.auth.service;

import java.time.Duration;
import java.time.Instant;

import link.srrrg.auth.model.LoginTicket;
import link.srrrg.auth.repository.LoginTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OAuth 리다이렉트에는 토큰 대신 짧은 수명의 일회용 교환권만 전달한다. */
@Service
@RequiredArgsConstructor
public class LoginTicketService {
	private static final Duration LIFETIME = Duration.ofMinutes(1);
	private final LoginTicketRepository repository;

	@Transactional
	public String issue(long userId) {
		Instant now = Instant.now();
		repository.deleteExpired(now);
		String raw = "srrrg_lt_" + TokenCodec.random();
		repository.saveAndFlush(new LoginTicket(TokenCodec.hash(raw), userId, now.plus(LIFETIME)));
		return raw;
	}

	@Transactional
	public long consume(String raw) {
		if (raw == null || !raw.matches("srrrg_lt_[A-Za-z0-9_-]{43}")) {
			throw new InvalidTicketException();
		}
		LoginTicket ticket = repository.lockValid(TokenCodec.hash(raw), Instant.now())
				.orElseThrow(InvalidTicketException::new);
		long userId = ticket.getUserId();
		// 잠금 중 삭제를 반영해야 병렬 교환 요청 중 하나만 성공한다.
		repository.delete(ticket);
		repository.flush();
		return userId;
	}

	public static class InvalidTicketException extends RuntimeException {
	}
}
