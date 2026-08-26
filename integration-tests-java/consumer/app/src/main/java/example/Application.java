package example;

import example.model.ModelContract;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        ModelContract.source();
        SpringApplication.run(Application.class, args);
    }
}
