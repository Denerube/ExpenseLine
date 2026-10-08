package be.rubikub.expenseline;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class ExpenseLineApplication {

    public static void main(String[] args) {
        System.out.println("DIT IS EEN TEST");
        Quarkus.run(args);
    }

}
