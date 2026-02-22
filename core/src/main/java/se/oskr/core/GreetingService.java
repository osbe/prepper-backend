package se.oskr.core;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GreetingService {

    @Transactional
    public String greet() {
        Greeting greeting = Greeting.find("FROM Greeting ORDER BY RANDOM()").firstResult();
        if (greeting == null) {
            Log.warn("No greetings found in database, using fallback");
            return "Hello";
        }
        Log.infof("Providing greeting: %s", greeting);
        return greeting.message;
    }
}
