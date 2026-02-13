package cloud.dashaun.service.javagrunt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "javagrunt.github")
public class GitHubAppProperties {
	private Long appId;
	private String webhookSecret;
	private String privateKeyPem;
	private String privateKeyPath;
	private String apiBaseUrl = "https://api.github.com";
	private String sharedRepoOwner = "dashaun-cloud";
	private String sharedRepoName = "github-shared-pipelines";
	private String sharedRepoPath = ".github/workflows/ci.yml";
	private String sharedRepoRef = "main";
	private String targetWorkflowPath = ".github/workflows/ci.yml";
	private String sharedRepoToken;
	private String advisorPath = "/usr/local/bin/advisor";
	private String advisorWorkspace = "./tmp/javagrunt";
	private int prCleanupDays = 30;
	private String advisorMappingGitUri = "https://github.com/dashaun-tanzu/advisor-mappings.git";
	private String advisorMappingGitPath = "mappings/";

	public Long getAppId() {
		return appId;
	}

	public void setAppId(Long appId) {
		this.appId = appId;
	}

	public String getWebhookSecret() {
		return webhookSecret;
	}

	public void setWebhookSecret(String webhookSecret) {
		this.webhookSecret = webhookSecret;
	}

	public String getPrivateKeyPem() {
		return privateKeyPem;
	}

	public void setPrivateKeyPem(String privateKeyPem) {
		this.privateKeyPem = privateKeyPem;
	}

	public String getPrivateKeyPath() {
		return privateKeyPath;
	}

	public void setPrivateKeyPath(String privateKeyPath) {
		this.privateKeyPath = privateKeyPath;
	}

	public String getApiBaseUrl() {
		return apiBaseUrl;
	}

	public void setApiBaseUrl(String apiBaseUrl) {
		this.apiBaseUrl = apiBaseUrl;
	}

	public String getSharedRepoOwner() {
		return sharedRepoOwner;
	}

	public void setSharedRepoOwner(String sharedRepoOwner) {
		this.sharedRepoOwner = sharedRepoOwner;
	}

	public String getSharedRepoName() {
		return sharedRepoName;
	}

	public void setSharedRepoName(String sharedRepoName) {
		this.sharedRepoName = sharedRepoName;
	}

	public String getSharedRepoPath() {
		return sharedRepoPath;
	}

	public void setSharedRepoPath(String sharedRepoPath) {
		this.sharedRepoPath = sharedRepoPath;
	}

	public String getSharedRepoRef() {
		return sharedRepoRef;
	}

	public void setSharedRepoRef(String sharedRepoRef) {
		this.sharedRepoRef = sharedRepoRef;
	}

	public String getTargetWorkflowPath() {
		return targetWorkflowPath;
	}

	public void setTargetWorkflowPath(String targetWorkflowPath) {
		this.targetWorkflowPath = targetWorkflowPath;
	}

	public String getSharedRepoToken() {
		return sharedRepoToken;
	}

	public void setSharedRepoToken(String sharedRepoToken) {
		this.sharedRepoToken = sharedRepoToken;
	}

	public String getAdvisorPath() {
		return advisorPath;
	}

	public void setAdvisorPath(String advisorPath) {
		this.advisorPath = advisorPath;
	}

	public String getAdvisorWorkspace() {
		return advisorWorkspace;
	}

	public void setAdvisorWorkspace(String advisorWorkspace) {
		this.advisorWorkspace = advisorWorkspace;
	}

	public int getPrCleanupDays() {
		return prCleanupDays;
	}

	public void setPrCleanupDays(int prCleanupDays) {
		this.prCleanupDays = prCleanupDays;
	}

	public String getAdvisorMappingGitUri() {
		return advisorMappingGitUri;
	}

	public void setAdvisorMappingGitUri(String advisorMappingGitUri) {
		this.advisorMappingGitUri = advisorMappingGitUri;
	}

	public String getAdvisorMappingGitPath() {
		return advisorMappingGitPath;
	}

	public void setAdvisorMappingGitPath(String advisorMappingGitPath) {
		this.advisorMappingGitPath = advisorMappingGitPath;
	}
}
