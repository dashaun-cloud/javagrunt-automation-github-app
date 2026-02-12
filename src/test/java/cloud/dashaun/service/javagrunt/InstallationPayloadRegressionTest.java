package cloud.dashaun.service.javagrunt;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import cloud.dashaun.service.javagrunt.service.GitHubApiClient;
import cloud.dashaun.service.javagrunt.service.InstallationService;
import cloud.dashaun.service.javagrunt.store.OrgRegistryStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstallationPayloadRegressionTest {
	@Test
	void createsOrgFromInstallationAccountPayload() throws Exception {
		String payload = """
		{
		  "action": "created",
		  "installation": {
		    "id": 109549278,
		    "account": {
		      "login": "dashaun-demo",
		      "type": "Organization"
		    }
		  },
		  "repositories": [
		    {
		      "id": 1095131316,
		      "name": "spring-petclinic",
		      "full_name": "dashaun-demo/spring-petclinic"
		    }
		  ]
		}
		""";

		GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
		OrgRegistryStore orgRegistryStore = mock(OrgRegistryStore.class);
		GitHubAppProperties properties = new GitHubAppProperties();
		properties.setSharedRepoOwner("dashaun-cloud");
		properties.setSharedRepoName("github-shared-pipelines");
		properties.setTargetWorkflowPath(".github/workflows/ci.yml");
		InstallationService service = new InstallationService(gitHubApiClient, properties, orgRegistryStore);

		when(gitHubApiClient.getInstallationToken(109549278L)).thenReturn("token");
		when(gitHubApiClient.fetchSharedWorkflow()).thenReturn("workflow");
		when(gitHubApiClient.contentExists("dashaun-demo", "spring-petclinic", ".github/workflows/ci.yml", "token"))
				.thenReturn(true);

		JsonNode node = new ObjectMapper().readTree(payload);
		service.handleInstallation(node);

		verify(orgRegistryStore).addOrg("dashaun-demo");
	}
}
