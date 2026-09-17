package link.srrrg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 초기 서버의 진입점이다.
 *
 * <p>현재는 상태 검사만 제공한다. 업무 기능과 저장소 연결은 이전 범위가 정해질 때 추가한다.</p>
 */
@SpringBootApplication
public class SrrrgApplication {

	public static void main(String[] args) {
		SpringApplication.run(SrrrgApplication.class, args);
	}
}
