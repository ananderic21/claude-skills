package dev.anand.claudeskills;

import org.springframework.boot.SpringApplication;

public class TestClaudeSkillsApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClaudeSkillsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
