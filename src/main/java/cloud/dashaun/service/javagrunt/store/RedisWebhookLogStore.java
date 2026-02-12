package cloud.dashaun.service.javagrunt.store;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisWebhookLogStore implements WebhookLogStore {
	private static final String WEBHOOK_LOG_KEY = "javagrunt:webhooks";
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public RedisWebhookLogStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public void logMessage(String event, String deliveryId, String signature, String payload, boolean signatureValid) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("received_at", Instant.now().toString());
		entry.put("event", event == null ? "" : event);
		entry.put("delivery_id", deliveryId == null ? "" : deliveryId);
		entry.put("signature", signature == null ? "" : signature);
		entry.put("signature_valid", signatureValid);
		entry.put("payload", payload == null ? "" : payload);
		try {
			String json = objectMapper.writeValueAsString(entry);
			redisTemplate.opsForList().leftPush(WEBHOOK_LOG_KEY, json);
		} catch (Exception ex) {
			redisTemplate.opsForList().leftPush(WEBHOOK_LOG_KEY, entry.toString());
		}
	}
}
