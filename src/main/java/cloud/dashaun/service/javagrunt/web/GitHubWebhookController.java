package cloud.dashaun.service.javagrunt.web;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import cloud.dashaun.service.javagrunt.service.InstallationService;
import cloud.dashaun.service.javagrunt.store.WebhookLogStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitHubWebhookController {
	private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);
	private final GitHubAppProperties properties;
	private final InstallationService installationService;
	private final ObjectMapper objectMapper;
	private final WebhookLogStore webhookLogStore;

	public GitHubWebhookController(GitHubAppProperties properties, InstallationService installationService, ObjectMapper objectMapper, WebhookLogStore webhookLogStore) {
		this.properties = properties;
		this.installationService = installationService;
		this.objectMapper = objectMapper;
		this.webhookLogStore = webhookLogStore;
	}

	@PostMapping("/webhook/github")
	public ResponseEntity<String> handleWebhook(
			@RequestHeader(name = "X-GitHub-Event", required = false) String event,
			@RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
			@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
			@RequestBody String payload) {
		boolean signatureValid = isSignatureValid(signature, payload);
		webhookLogStore.logMessage(event, deliveryId, signature, payload, signatureValid);
		if (!signatureValid) {
			log.warn("Invalid webhook signature");
			return ResponseEntity.status(401).body("invalid signature");
		}
		if (!"installation".equals(event)) {
			return ResponseEntity.ok("ignored");
		}
		try {
			JsonNode body = objectMapper.readTree(payload);
			installationService.handleInstallation(body);
			return ResponseEntity.ok("ok");
		} catch (Exception ex) {
			log.warn("Failed to handle installation webhook", ex);
			return ResponseEntity.status(500).body("error");
		}
	}

	private boolean isSignatureValid(String signatureHeader, String payload) {
		String secret = properties.getWebhookSecret();
		if (secret == null || secret.isBlank()) {
			return true;
		}
		if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
			return false;
		}
		String expected = "sha256=" + hmacSha256(secret, payload);
		return constantTimeEquals(expected, signatureHeader);
	}

	private String hmacSha256(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to compute webhook signature", ex);
		}
	}

	private boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}
}
