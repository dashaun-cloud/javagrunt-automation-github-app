package cloud.dashaun.service.javagrunt;

import cloud.dashaun.service.javagrunt.config.GitHubAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GitHubAppProperties.class)
public class JavagruntApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavagruntApplication.class, args);
	}
}
