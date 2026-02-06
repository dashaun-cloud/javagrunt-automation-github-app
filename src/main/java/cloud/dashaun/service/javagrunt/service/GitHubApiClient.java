package cloud.dashaun.service.javagrunt.service;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubApiClient {
	private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
	private final GitHubAppProperties properties;
	private final GitHubAppJwtProvider jwtProvider;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public GitHubApiClient(GitHubAppProperties properties, GitHubAppJwtProvider jwtProvider, ObjectMapper objectMapper) {
		this.properties = properties;
		this.jwtProvider = jwtProvider;
		this.objectMapper = objectMapper;
	}

	public String getInstallationToken(long installationId) {
		String jwt = jwtProvider.createJwt();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(apiUri("/app/installations/" + installationId + "/access_tokens"))
				.header("Authorization", "Bearer " + jwt)
				.header("Accept", "application/vnd.github+json")
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
		JsonNode response = sendJson(request);
		return response.path("token").asText();
	}

	public String getDefaultBranch(String owner, String repo, String token) {
		HttpRequest request = baseRequest("/repos/" + owner + "/" + repo, token)
				.GET()
				.build();
		JsonNode response = sendJson(request);
		return response.path("default_branch").asText("main");
	}

	public String getRefSha(String owner, String repo, String ref, String token) {
		HttpRequest request = baseRequest("/repos/" + owner + "/" + repo + "/git/ref/heads/" + ref, token)
				.GET()
				.build();
		JsonNode response = sendJson(request);
		return response.path("object").path("sha").asText();
	}

	public boolean contentExists(String owner, String repo, String path, String token) {
		HttpRequest request = baseRequest("/repos/" + owner + "/" + repo + "/contents/" + path, token)
				.GET()
				.build();
		return sendOptionalJson(request).isPresent();
	}

	public String fetchSharedWorkflow() {
		String owner = properties.getSharedRepoOwner();
		String repo = properties.getSharedRepoName();
		String path = properties.getSharedRepoPath();
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(apiUri("/repos/" + owner + "/" + repo + "/contents/" + path))
				.header("Accept", "application/vnd.github.raw");
		String token = properties.getSharedRepoToken();
		if (token != null && !token.isBlank()) {
			builder.header("Authorization", "Bearer " + token);
		}
		HttpRequest request = builder.GET().build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Failed to fetch shared workflow: " + response.statusCode());
		}
		return response.body();
	}

	public void createBranch(String owner, String repo, String branch, String sha, String token) {
		String payload = "{\"ref\":\"refs/heads/" + branch + "\",\"sha\":\"" + sha + "\"}";
		HttpRequest request = baseRequest("/repos/" + owner + "/" + repo + "/git/refs", token)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
		sendJson(request);
	}

	public void putFile(String owner, String repo, String path, String branch, String message, String content, String token) {
		String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
		String payload = objectMapper.createObjectNode()
				.put("message", message)
				.put("content", encoded)
				.put("branch", branch)
				.toString();
		HttpRequest request = baseRequest("/repos/" + owner + "/" + repo + "/contents/" + path, token)
				.PUT(HttpRequest.BodyPublishers.ofString(payload))
				.build();
		sendJson(request);
	}

	public void createPullRequest(String owner, String repo, String title, String body, String head, String base, String token) {
		String payload = objectMapper.createObjectNode()
				.put("title", title)
				.put("body", body)
				.put("head", head)
				.put("base", base)
				.toString();
		HttpRequest request = baseRequest("/repos/" + owner + "/" + repo + "/pulls", token)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
		sendJson(request);
	}

	private HttpRequest.Builder baseRequest(String path, String token) {
		return HttpRequest.newBuilder()
				.uri(apiUri(path))
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.header("User-Agent", "javagrunt-github-app");
	}

	private URI apiUri(String path) {
		String base = properties.getApiBaseUrl();
		return URI.create(base + path);
	}

	private JsonNode sendJson(HttpRequest request) {
		HttpResponse<String> response = send(request);
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			log.warn("GitHub API error {} for {}", response.statusCode(), request.uri());
			throw new IllegalStateException("GitHub API error: " + response.statusCode());
		}
		try {
			return objectMapper.readTree(response.body());
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Failed to parse GitHub API response", ex);
		}
	}

	private Optional<JsonNode> sendOptionalJson(HttpRequest request) {
		HttpResponse<String> response = send(request);
		if (response.statusCode() == 404) {
			return Optional.empty();
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			log.warn("GitHub API error {} for {}", response.statusCode(), request.uri());
			throw new IllegalStateException("GitHub API error: " + response.statusCode());
		}
		try {
			return Optional.of(objectMapper.readTree(response.body()));
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Failed to parse GitHub API response", ex);
		}
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Failed to call GitHub API", ex);
		}
	}

	public String buildBranchName() {
		return "javagrunt/ci-" + Instant.now().toString().replace(":", "").replace("-", "").replace("T", "").split("\\.")[0];
	}
}
