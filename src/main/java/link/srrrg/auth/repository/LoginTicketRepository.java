package link.srrrg.auth.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import link.srrrg.auth.model.LoginTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoginTicketRepository extends JpaRepository<LoginTicket, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select ticket from LoginTicket ticket where ticket.ticketHash = :hash and ticket.expiresAt > :now")
	Optional<LoginTicket> lockValid(String hash, Instant now);

	@Modifying
	@Query("delete from LoginTicket ticket where ticket.expiresAt <= :now")
	int deleteExpired(Instant now);
}
