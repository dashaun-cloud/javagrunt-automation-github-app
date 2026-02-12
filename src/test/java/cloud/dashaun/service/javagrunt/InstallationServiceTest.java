package cloud.dashaun.service.javagrunt;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import cloud.dashaun.service.javagrunt.service.GitHubApiClient;
import cloud.dashaun.service.javagrunt.service.InstallationService;
import cloud.dashaun.service.javagrunt.store.OrgRegistryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstallationServiceTest {
	private GitHubApiClient gitHubApiClient;
	private GitHubAppProperties properties;
	private InstallationService service;
	private ObjectMapper mapper;
	private OrgRegistryStore orgRegistryStore;

	@BeforeEach
	void setUp() {
		gitHubApiClient = mock(GitHubApiClient.class);
		orgRegistryStore = mock(OrgRegistryStore.class);
		properties = new GitHubAppProperties();
		properties.setSharedRepoOwner("dashaun-cloud");
		properties.setSharedRepoName("github-shared-pipelines");
		properties.setTargetWorkflowPath(".github/workflows/ci.yml");
		service = new InstallationService(gitHubApiClient, properties, orgRegistryStore);
		mapper = new ObjectMapper();
	}

	@Test
	void createsPullRequestWhenWorkflowMissing() {
		ObjectNode payload = installationPayload("created");

		when(gitHubApiClient.getInstallationToken(42L)).thenReturn("token");
		when(gitHubApiClient.fetchSharedWorkflow()).thenReturn("workflow");
		when(gitHubApiClient.contentExists("org", "demo", ".github/workflows/ci.yml", "token")).thenReturn(false);
		when(gitHubApiClient.getDefaultBranch("org", "demo", "token")).thenReturn("main");
		when(gitHubApiClient.getRefSha("org", "demo", "main", "token")).thenReturn("sha");
		when(gitHubApiClient.buildBranchName()).thenReturn("javagrunt/ci-branch");

		service.handleInstallation(payload);

		verify(orgRegistryStore).addOrg("org");
		verify(gitHubApiClient).createBranch("org", "demo", "javagrunt/ci-branch", "sha", "token");
		verify(gitHubApiClient).putFile(
				"org",
				"demo",
				".github/workflows/ci.yml",
				"javagrunt/ci-branch",
				"Add centralized CI workflow",
				"workflow",
				"token");
		verify(gitHubApiClient).createPullRequest(
				"org",
				"demo",
				"Add centralized CI workflow",
				"This PR adds the shared CI workflow from dashaun-cloud/github-shared-pipelines.",
				"javagrunt/ci-branch",
				"main",
				"token");
	}

	@Test
	void skipsWhenWorkflowExists() {
		ObjectNode payload = installationPayload("created");

		when(gitHubApiClient.getInstallationToken(42L)).thenReturn("token");
		when(gitHubApiClient.fetchSharedWorkflow()).thenReturn("workflow");
		when(gitHubApiClient.contentExists("org", "demo", ".github/workflows/ci.yml", "token")).thenReturn(true);

		service.handleInstallation(payload);

		verify(orgRegistryStore).addOrg("org");
		verify(gitHubApiClient, never()).createBranch("org", "demo", "javagrunt/ci-branch", "sha", "token");
		verify(gitHubApiClient, never()).putFile(
				"org",
				"demo",
				".github/workflows/ci.yml",
				"javagrunt/ci-branch",
				"Add centralized CI workflow",
				"workflow",
				"token");
		verify(gitHubApiClient, never()).createPullRequest(
				"org",
				"demo",
				"Add centralized CI workflow",
				"This PR adds the shared CI workflow from dashaun-cloud/github-shared-pipelines.",
				"javagrunt/ci-branch",
				"main",
				"token");
	}

	@Test
	void ignoresOtherActions() {
		ObjectNode payload = installationPayload("deleted");

		service.handleInstallation(payload);

		verify(orgRegistryStore).setOrgStatus("org", OrgRegistryStore.OrgStatus.DELETED);
		verifyNoInteractions(gitHubApiClient);
	}

	@Test
	void skipsReposMissingOwnerLogin() {
		ObjectNode payload = installationPayload("created");
		ArrayNode repos = (ArrayNode) payload.get("repositories");
		repos.addObject().put("name", "missing-owner");

		when(gitHubApiClient.getInstallationToken(42L)).thenReturn("token");
		when(gitHubApiClient.fetchSharedWorkflow()).thenReturn("workflow");
		when(gitHubApiClient.contentExists("org", "demo", ".github/workflows/ci.yml", "token")).thenReturn(true);

		service.handleInstallation(payload);

		verify(gitHubApiClient, never()).createPullRequest(
				"org",
				"demo",
				"Add centralized CI workflow",
				"This PR adds the shared CI workflow from dashaun-cloud/github-shared-pipelines.",
				"javagrunt/ci-branch",
				"main",
				"token");
	}

	private ObjectNode installationPayload(String action) {
		ObjectNode root = mapper.createObjectNode();
		root.put("action", action);
		ObjectNode installation = root.putObject("installation");
		installation.put("id", 42L);
		installation.putObject("account").put("login", "org");
		ArrayNode repos = root.putArray("repositories");
		ObjectNode repo = repos.addObject();
		repo.put("name", "demo");
		repo.put("full_name", "org/demo");
		repo.putObject("owner").put("login", "org");
		return root;
	}
}
