package cloud.dashaun.service.javagrunt.service;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class GitHubAppJwtProvider {
	private final GitHubAppProperties properties;
	private volatile PrivateKey cachedKey;

	public GitHubAppJwtProvider(GitHubAppProperties properties) {
		this.properties = properties;
	}

	public String createJwt() {
		Long appId = properties.getAppId();
		if (appId == null) {
			throw new IllegalStateException("GitHub App id is not configured");
		}
		long now = Instant.now().getEpochSecond();
		long iat = now - 60;
		long exp = now + 540;
		String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
		String payloadJson = "{\"iat\":" + iat + ",\"exp\":" + exp + ",\"iss\":" + appId + "}";
		String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
		String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
		String signingInput = header + "." + payload;
		byte[] signature = sign(signingInput.getBytes(StandardCharsets.UTF_8));
		return signingInput + "." + base64Url(signature);
	}

	private byte[] sign(byte[] input) {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(loadPrivateKey());
			signature.update(input);
			return signature.sign();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to sign GitHub App JWT", ex);
		}
	}

	private PrivateKey loadPrivateKey() {
		PrivateKey key = cachedKey;
		if (key != null) {
			return key;
		}
		synchronized (this) {
			if (cachedKey == null) {
				cachedKey = parsePrivateKey(loadPem());
			}
			return cachedKey;
		}
	}

	private String loadPem() {
		String pem = properties.getPrivateKeyPem();
		if (pem != null && !pem.isBlank()) {
			return pem;
		}
		String path = properties.getPrivateKeyPath();
		if (path == null || path.isBlank()) {
			throw new IllegalStateException("GitHub App private key is not configured");
		}
		try {
			return Files.readString(Path.of(path));
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to read GitHub App private key from " + path, ex);
		}
	}

	private PrivateKey parsePrivateKey(String pem) {
		String normalized = pem
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		byte[] decoded = Base64.getDecoder().decode(normalized);
		try {
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePrivate(spec);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to parse GitHub App private key", ex);
		}
	}

	private String base64Url(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}
}
