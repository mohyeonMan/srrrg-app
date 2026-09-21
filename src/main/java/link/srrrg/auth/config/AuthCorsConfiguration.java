package link.srrrg.auth.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class AuthCorsConfiguration {
	@Bean
	CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (properties.frontendUrl() == null || properties.frontendUrl().isBlank()) {
			return source;
		}
		org.springframework.web.cors.CorsConfiguration cors = new org.springframework.web.cors.CorsConfiguration();
		cors.setAllowedOrigins(List.of(properties.frontendUrl().replaceAll("/+$", "")));
		cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setAllowCredentials(false);
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}
