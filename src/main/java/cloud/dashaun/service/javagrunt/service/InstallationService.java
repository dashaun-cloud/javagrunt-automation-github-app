package cloud.dashaun.service.javagrunt.service;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import cloud.dashaun.service.javagrunt.store.OrgRegistryStore;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InstallationService {
	private static final Logger log = LoggerFactory.getLogger(InstallationService.class);
	private final GitHubApiClient gitHubApiClient;
	private final GitHubAppProperties properties;
	private final OrgRegistryStore orgRegistryStore;

	public InstallationService(GitHubApiClient gitHubApiClient, GitHubAppProperties properties, OrgRegistryStore orgRegistryStore) {
		this.gitHubApiClient = gitHubApiClient;
		this.properties = properties;
		this.orgRegistryStore = orgRegistryStore;
	}

	public void handleInstallation(JsonNode payload) {
		String action = payload.path("action").asText("");
		String accountLogin = payload.path("installation").path("account").path("login").asText("");
		if (action.equals("deleted")) {
			if (!accountLogin.isBlank()) {
				orgRegistryStore.setOrgStatus(accountLogin, OrgRegistryStore.OrgStatus.DELETED);
			}
			log.info("Marked org {} as deleted", accountLogin);
			return;
		}
		if (!action.equals("created") && !action.equals("repositories_added")) {
			log.info("Ignoring installation action {}", action);
			return;
		}
		long installationId = payload.path("installation").path("id").asLong();
		List<RepoRef> repos = extractRepos(payload);
		if (!accountLogin.isBlank()) {
			orgRegistryStore.addOrg(accountLogin);
			if (installationId > 0) {
				orgRegistryStore.setInstallationId(accountLogin, installationId);
			}
		}
		if (repos.isEmpty()) {
			log.warn("No repositories found in installation payload");
			return;
		}
		String token = gitHubApiClient.getInstallationToken(installationId);
		String workflow = gitHubApiClient.fetchSharedWorkflow();
		for (RepoRef repo : repos) {
			try {
				orgRegistryStore.addRepo(repo.owner, repo.name);
				ensureWorkflow(repo, token, workflow);
			} catch (Exception ex) {
				log.warn("Failed to provision workflow for {}: {}", repo.fullName, ex.getMessage());
			}
		}
	}

	private void ensureWorkflow(RepoRef repo, String token, String workflow) {
		if (gitHubApiClient.contentExists(repo.owner, repo.name, properties.getTargetWorkflowPath(), token)) {
			log.info("Workflow already exists in {}", repo.fullName);
			return;
		}
		String baseBranch = gitHubApiClient.getDefaultBranch(repo.owner, repo.name, token);
		String baseSha = gitHubApiClient.getRefSha(repo.owner, repo.name, baseBranch, token);
		String branch = gitHubApiClient.buildBranchName();
		gitHubApiClient.createBranch(repo.owner, repo.name, branch, baseSha, token);
		String message = "Add centralized CI workflow";
		gitHubApiClient.putFile(repo.owner, repo.name, properties.getTargetWorkflowPath(), branch, message, workflow, token);
		String title = "Add centralized CI workflow";
		String body = "This PR adds the shared CI workflow from " + properties.getSharedRepoOwner() + "/" + properties.getSharedRepoName() + ".";
		gitHubApiClient.createPullRequest(repo.owner, repo.name, title, body, branch, baseBranch, token);
		log.info("Created CI workflow PR for {}", repo.fullName);
	}

	private List<RepoRef> extractRepos(JsonNode payload) {
		List<RepoRef> repos = new ArrayList<>();
		addRepoList(payload.path("repositories"), repos);
		addRepoList(payload.path("repositories_added"), repos);
		return repos;
	}

	private void addRepoList(JsonNode nodes, List<RepoRef> repos) {
		if (!nodes.isArray()) {
			return;
		}
		for (JsonNode repo : nodes) {
			JsonNode nameNode = repo.path("name");
			if (nameNode.isMissingNode()) {
				continue;
			}
			String name = nameNode.asText();
			String owner = repo.path("owner").path("login").asText("");
			String fullName = repo.path("full_name").isMissingNode() ? "" : repo.path("full_name").asText();
			if ((owner == null || owner.isBlank()) && fullName.contains("/")) {
				owner = fullName.substring(0, fullName.indexOf('/'));
			}
			if (name == null || name.isBlank() || owner == null || owner.isBlank()) {
				continue;
			}
			repos.add(new RepoRef(owner, name, fullName.isBlank() ? owner + "/" + name : fullName));
		}
	}

	private record RepoRef(String owner, String name, String fullName) {}
}
