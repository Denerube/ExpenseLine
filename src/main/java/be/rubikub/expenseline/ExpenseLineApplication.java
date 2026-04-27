package be.rubikub.expenseline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExpenseLineApplication {

    public static void main(String[] args) {

        SpringApplication.run(ExpenseLineApplication.class, args);
        IO.println("DIT IS EEN TEST");
    }

}
