package cloud.dashaun.service.javagrunt.service;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import cloud.dashaun.service.javagrunt.service.GitHubApiClient.PullRequestInfo;
import cloud.dashaun.service.javagrunt.store.OrgRegistryStore;
import cloud.dashaun.service.javagrunt.store.OrgRegistryStore.OrgStatus;
import cloud.dashaun.service.javagrunt.store.OrgRegistryStore.OrgStatusEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RepoAdvisorScheduler {
	private static final Logger log = LoggerFactory.getLogger(RepoAdvisorScheduler.class);
	private final OrgRegistryStore orgRegistryStore;
	private final GitHubApiClient gitHubApiClient;
	private final GitHubAppProperties properties;

	public RepoAdvisorScheduler(OrgRegistryStore orgRegistryStore, GitHubApiClient gitHubApiClient, GitHubAppProperties properties) {
		this.orgRegistryStore = orgRegistryStore;
		this.gitHubApiClient = gitHubApiClient;
		this.properties = properties;
	}

	@Scheduled(cron = "${javagrunt.scheduler.cron:0 0 */4 * * *}")
	public void runAdvisor() {
		runAdvisorInternal();
	}

	@Async
	public void runAdvisorNow() {
		runAdvisorInternal();
	}

	private void runAdvisorInternal() {
		List<OrgStatusEntry> orgs = orgRegistryStore.listOrgStatuses();
		if (orgs.isEmpty()) {
			log.info("No orgs registered; skipping advisor run");
			return;
		}
		for (OrgStatusEntry entry : orgs) {
			if (entry.status() == OrgStatus.DELETED) {
				continue;
			}
			String org = entry.org();
			Long installationId = orgRegistryStore.getInstallationId(org);
			if (installationId == null) {
				log.warn("Missing installation id for org {}", org);
				continue;
			}
			String token = gitHubApiClient.getInstallationToken(installationId);
			for (String repo : orgRegistryStore.listRepos(org)) {
				runAdvisorForRepo(org, repo, token);
			}
		}
	}

	private void runAdvisorForRepo(String org, String repo, String token) {
		String workspaceRoot = properties.getAdvisorWorkspace();
		String runId = Instant.now().toString().replace(":", "").replace("-", "").replace("T", "").split("\\.")[0];
		Path repoDir = Path.of(workspaceRoot, org, repo, runId);
		try {
			Files.createDirectories(repoDir);
			cloneRepo(org, repo, token, repoDir);
			boolean skip = cleanupOldPrsIfNeeded(org, repo, token);
			if (skip) {
				log.info("Skipping advisor run for {}/{} due to recent [Auto] PR", org, repo);
				return;
			}
			runAdvisor(repoDir);
			String upgradePlan = getUpgradePlan(repoDir);
			if (upgradePlan.contains("No upgrade plans available")) {
				applyPatchUpgrade(org, repo, token, repoDir);
			} else {
				applyAdvisorUpgrade(repoDir);
			}
		} catch (Exception ex) {
			log.warn("Advisor run failed for {}/{}: {}", org, repo, ex.getMessage());
		}
	}

	private void cloneRepo(String org, String repo, String token, Path repoDir) throws IOException, InterruptedException {
		String url = "https://x-access-token:" + token + "@github.com/" + org + "/" + repo + ".git";
		ProcessBuilder builder = new ProcessBuilder("git", "clone", "--depth", "1", url, ".")
				.directory(repoDir.toFile())
				.redirectErrorStream(true);
		Process process = builder.start();
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("git clone failed with exit code " + code);
		}
	}

	private void runAdvisor(Path repoDir) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(properties.getAdvisorPath(), "build-config", "get")
				.directory(repoDir.toFile())
				.redirectErrorStream(true);
			builder.environment().put("SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_URI", properties.getAdvisorMappingGitUri());
			builder.environment().put("SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_PATH", properties.getAdvisorMappingGitPath());
		Process process = builder.start();
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("advisor failed with exit code " + code);
		}
	}

	private String getUpgradePlan(Path repoDir) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(properties.getAdvisorPath(), "upgrade-plan", "get")
				.directory(repoDir.toFile())
				.redirectErrorStream(true);
		builder.environment().put("SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_URI", properties.getAdvisorMappingGitUri());
		builder.environment().put("SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_PATH", properties.getAdvisorMappingGitPath());
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes());
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("advisor upgrade-plan failed with exit code " + code);
		}
		return output;
	}

	private void applyAdvisorUpgrade(Path repoDir) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(properties.getAdvisorPath(), "upgrade-plan", "apply", "--push")
				.directory(repoDir.toFile())
				.redirectErrorStream(true);
		builder.environment().put("SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_URI", properties.getAdvisorMappingGitUri());
		builder.environment().put("SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_PATH", properties.getAdvisorMappingGitPath());
		Process process = builder.start();
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("advisor upgrade-plan apply failed with exit code " + code);
		}
	}

	private void applyPatchUpgrade(String org, String repo, String token, Path repoDir) throws IOException, InterruptedException {
		runCommand(repoDir, "./mvnw", "org.openrewrite.maven:rewrite-maven-plugin:run",
				"-Drewrite.configLocation=https://raw.githubusercontent.com/dashaun-tanzu/openrewrite-recipes/refs/heads/main/MavenUpgradeSpringBootToLatestPatch.yaml",
				"-Drewrite.activeRecipes=com.dashaun.openrewrite.MavenUpgradeSpringBootToLatestPatch");
		if (!hasChanges(repoDir)) {
			return;
		}
		String branchName = "patch-upgrade-" + Instant.now().toString().replace(":", "").replace("-", "").replace("T", "").split("\\.")[0];
		String prTitle = "[Auto] Spring Boot Patch Upgrade";
		runCommand(repoDir, "git", "checkout", "-b", branchName);
		runCommand(repoDir, "git", "add", ".");
		runCommand(repoDir, "git", "commit", "-m", prTitle + " - " + Instant.now());
		String url = "https://x-access-token:" + token + "@github.com/" + org + "/" + repo + ".git";
		runCommand(repoDir, "git", "push", "-u", url, branchName);
		gitHubApiClient.createPullRequest(org, repo, prTitle, prTitle, branchName, "main", token);
	}

	private boolean hasChanges(Path repoDir) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder("git", "status", "--porcelain")
				.directory(repoDir.toFile())
				.redirectErrorStream(true);
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes());
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("git status failed with exit code " + code);
		}
		return !output.trim().isEmpty();
	}

	private boolean cleanupOldPrsIfNeeded(String org, String repo, String token) {
		List<PullRequestInfo> prs = gitHubApiClient.listOpenPullRequests(org, repo, token);
		boolean recentFound = false;
		int olderThanDays = properties.getPrCleanupDays();
		OffsetDateTime now = OffsetDateTime.now();
		for (PullRequestInfo pr : prs) {
			if (pr.title() == null || !pr.title().startsWith("[Auto]")) {
				continue;
			}
			OffsetDateTime created = OffsetDateTime.parse(pr.created_at());
			long ageDays = Duration.between(created, now).toDays();
			if (ageDays > olderThanDays) {
				log.info("Closing old [Auto] PR #{} for {}/{} ({} days)", pr.number(), org, repo, ageDays);
				gitHubApiClient.closePullRequest(org, repo, pr.number(), token);
				if (pr.head() != null && pr.head().ref() != null) {
					gitHubApiClient.deleteBranch(org, repo, pr.head().ref(), token);
				}
			} else {
				recentFound = true;
			}
		}
		return recentFound;
	}

	private void runCommand(Path repoDir, String... command) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(repoDir.toFile())
				.redirectErrorStream(true);
		Process process = builder.start();
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("Command failed: " + String.join(" ", command));
		}
	}
}
