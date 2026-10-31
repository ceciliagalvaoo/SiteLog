package hr.algebra.sitelog;

import org.springframework.boot.SpringApplication;

public class TestSiteLogApplication {

	public static void main(String[] args) {
		SpringApplication.from(SiteLogApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
