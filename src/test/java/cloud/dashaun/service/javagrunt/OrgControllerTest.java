package cloud.dashaun.service.javagrunt;

import cloud.dashaun.service.javagrunt.store.OrgRegistryStore;
import cloud.dashaun.service.javagrunt.web.OrgController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrgController.class)
class OrgControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrgRegistryStore orgRegistryStore;

	@Test
	void listsOrgs() throws Exception {
		when(orgRegistryStore.listOrgStatuses()).thenReturn(List.of(
				new OrgRegistryStore.OrgStatusEntry("dashaun-cloud", OrgRegistryStore.OrgStatus.ACTIVE),
				new OrgRegistryStore.OrgStatusEntry("dashaun-dev", OrgRegistryStore.OrgStatus.DELETED)
		));

		mockMvc.perform(get("/orgs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orgs[0].org").value("dashaun-cloud"))
				.andExpect(jsonPath("$.orgs[0].status").value("active"))
				.andExpect(jsonPath("$.orgs[1].org").value("dashaun-dev"))
				.andExpect(jsonPath("$.orgs[1].status").value("deleted"));
	}

	@Test
	void listsReposForOrg() throws Exception {
		when(orgRegistryStore.listRepos("dashaun-dev")).thenReturn(List.of("repo-a", "repo-b"));

		mockMvc.perform(get("/orgs/dashaun-dev/repos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repos[0]").value("repo-a"))
				.andExpect(jsonPath("$.repos[1]").value("repo-b"));
	}
}
