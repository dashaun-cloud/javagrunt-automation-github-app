package cloud.dashaun.service.javagrunt.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuthCallbackController {
	@GetMapping("/login/oauth2/code/github")
	public ResponseEntity<String> handleCallback(@RequestParam(name = "code", required = false) String code) {
		if (code == null || code.isBlank()) {
			return ResponseEntity.badRequest().body("missing code");
		}
		return ResponseEntity.ok("received");
	}
}
