package ru.auriny.omsuschedulebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OmsuScheduleBotApplication {
	public static void main(String[] args) {
		SpringApplication.run(OmsuScheduleBotApplication.class, args);

		new SpringApplicationBuilder(OmsuScheduleBotApplication.class)
				.sources(OmsuScheduleBotApplication.class)
				.run(args);
	}

}
