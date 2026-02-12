package cloud.dashaun.service.javagrunt.store;

public interface WebhookLogStore {
	void logMessage(String event, String deliveryId, String signature, String payload, boolean signatureValid);
}
