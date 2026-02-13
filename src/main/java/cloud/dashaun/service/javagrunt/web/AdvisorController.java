package cloud.dashaun.service.javagrunt.web;

import cloud.dashaun.service.javagrunt.service.RepoAdvisorScheduler;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvisorController {
	private final RepoAdvisorScheduler repoAdvisorScheduler;

	public AdvisorController(RepoAdvisorScheduler repoAdvisorScheduler) {
		this.repoAdvisorScheduler = repoAdvisorScheduler;
	}

	@GetMapping("/runall")
	public ResponseEntity<Map<String, String>> runAll() {
		repoAdvisorScheduler.runAdvisorNow();
		return ResponseEntity.ok(Map.of("status", "started"));
	}
}
