package pl.perigee.gauge;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.util.Locale;

@QuarkusMain
public class Main {
    public static void main(String... args) {
        Locale.setDefault(Locale.US);
        Quarkus.run(args);
    }
}
