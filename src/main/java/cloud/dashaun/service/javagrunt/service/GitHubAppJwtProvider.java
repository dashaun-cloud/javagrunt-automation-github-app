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
		boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
		String normalized = pem
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replace("-----BEGIN RSA PRIVATE KEY-----", "")
				.replace("-----END RSA PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		byte[] decoded = Base64.getDecoder().decode(normalized);
		if (pkcs1) {
			decoded = wrapPkcs1InPkcs8(decoded);
		}
		try {
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePrivate(spec);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to parse GitHub App private key", ex);
		}
	}

	private byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
		// PKCS#8 = SEQUENCE( version, algorithmIdentifier, OCTET STRING(pkcs1) )
		byte[] oid = new byte[] {0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01};
		byte[] nullParam = new byte[] {0x05, 0x00};
		byte[] algId = concat(new byte[] {0x30, (byte) (oid.length + nullParam.length)}, oid, nullParam);
		byte[] pkcs1Octet = concat(new byte[] {0x04}, encodeLength(pkcs1.length), pkcs1);
		byte[] version = new byte[] {0x02, 0x01, 0x00};
		byte[] body = concat(version, algId, pkcs1Octet);
		return concat(new byte[] {0x30}, encodeLength(body.length), body);
	}

	private byte[] encodeLength(int length) {
		if (length < 0x80) {
			return new byte[] {(byte) length};
		}
		int temp = length;
		int numBytes = 0;
		while (temp > 0) {
			temp >>= 8;
			numBytes++;
		}
		byte[] result = new byte[1 + numBytes];
		result[0] = (byte) (0x80 | numBytes);
		for (int i = numBytes; i > 0; i--) {
			result[i] = (byte) (length & 0xff);
			length >>= 8;
		}
		return result;
	}

	private byte[] concat(byte[]... parts) {
		int total = 0;
		for (byte[] part : parts) {
			total += part.length;
		}
		byte[] combined = new byte[total];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, combined, offset, part.length);
			offset += part.length;
		}
		return combined;
	}

	private String base64Url(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}
}
