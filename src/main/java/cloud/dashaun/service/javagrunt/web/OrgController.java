package cloud.dashaun.service.javagrunt.web;

import cloud.dashaun.service.javagrunt.store.OrgRegistryStore;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrgController {
	private final OrgRegistryStore orgRegistryStore;

	public OrgController(OrgRegistryStore orgRegistryStore) {
		this.orgRegistryStore = orgRegistryStore;
	}

	@GetMapping("/orgs")
	public ResponseEntity<Map<String, List<Map<String, String>>>> listOrgs() {
		List<Map<String, String>> orgs = orgRegistryStore.listOrgStatuses().stream()
				.map(entry -> Map.of("org", entry.org(), "status", entry.status().name().toLowerCase()))
				.toList();
		return ResponseEntity.ok(Map.of("orgs", orgs));
	}

	@GetMapping("/orgs/{org}/repos")
	public ResponseEntity<Map<String, List<String>>> listRepos(@PathVariable("org") String org) {
		return ResponseEntity.ok(Map.of("repos", orgRegistryStore.listRepos(org)));
	}
}
