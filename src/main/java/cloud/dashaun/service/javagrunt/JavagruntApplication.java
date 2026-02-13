package cloud.dashaun.service.javagrunt;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(GitHubAppProperties.class)
@EnableScheduling
@EnableAsync
public class JavagruntApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavagruntApplication.class, args);
	}
}
