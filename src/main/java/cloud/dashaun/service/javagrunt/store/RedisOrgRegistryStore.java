package cloud.dashaun.service.javagrunt.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisOrgRegistryStore implements OrgRegistryStore {
	private static final String ORGS_KEY = "javagrunt:orgs";
	private final StringRedisTemplate redisTemplate;

	public RedisOrgRegistryStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void addOrg(String org) {
		setOrgStatus(org, OrgStatus.ACTIVE);
	}

	@Override
	public void addRepo(String org, String repo) {
		setOrgStatus(org, OrgStatus.ACTIVE);
		redisTemplate.opsForSet().add(reposKey(org), repo);
	}

	@Override
	public void setOrgStatus(String org, OrgStatus status) {
		redisTemplate.opsForHash().put(ORGS_KEY, org, status.name().toLowerCase());
	}

	@Override
	public List<String> listOrgs() {
		Set<Object> orgs = redisTemplate.opsForHash().keys(ORGS_KEY);
		if (orgs == null || orgs.isEmpty()) {
			return List.of();
		}
		ArrayList<String> sorted = new ArrayList<>(orgs.size());
		for (Object org : orgs) {
			sorted.add(String.valueOf(org));
		}
		Collections.sort(sorted);
		return sorted;
	}

	@Override
	public List<String> listRepos(String org) {
		Set<String> repos = redisTemplate.opsForSet().members(reposKey(org));
		if (repos == null || repos.isEmpty()) {
			return List.of();
		}
		ArrayList<String> sorted = new ArrayList<>(repos);
		Collections.sort(sorted);
		return sorted;
	}

	@Override
	public List<OrgStatusEntry> listOrgStatuses() {
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(ORGS_KEY);
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}
		ArrayList<OrgStatusEntry> results = new ArrayList<>(entries.size());
		for (Map.Entry<Object, Object> entry : entries.entrySet()) {
			String org = String.valueOf(entry.getKey());
			String statusValue = String.valueOf(entry.getValue());
			OrgStatus status = "deleted".equalsIgnoreCase(statusValue) ? OrgStatus.DELETED : OrgStatus.ACTIVE;
			results.add(new OrgStatusEntry(org, status));
		}
		results.sort((a, b) -> a.org().compareToIgnoreCase(b.org()));
		return results;
	}

	private String reposKey(String org) {
		return "javagrunt:org:" + org + ":repos";
	}
}
