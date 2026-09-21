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
@Table(name = "login_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginTicket {
	@Id
	@Column(name = "ticket_hash", length = 64)
	private String ticketHash;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public LoginTicket(String ticketHash, long userId, Instant expiresAt) {
		this.ticketHash = ticketHash;
		this.userId = userId;
		this.expiresAt = expiresAt;
	}
}
