package cloud.dashaun.service.javagrunt.store;

import java.util.List;

public interface OrgRegistryStore {
	void addOrg(String org);

	void addRepo(String org, String repo);

	void setOrgStatus(String org, OrgStatus status);

	void setInstallationId(String org, long installationId);

	Long getInstallationId(String org);

	List<String> listOrgs();

	List<String> listRepos(String org);

	List<OrgStatusEntry> listOrgStatuses();

	enum OrgStatus {
		ACTIVE,
		DELETED
	}

	record OrgStatusEntry(String org, OrgStatus status) {}
}
