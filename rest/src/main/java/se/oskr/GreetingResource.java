package se.oskr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.oskr.api.HelloApi;
import se.oskr.core.GreetingService;

@ApplicationScoped
public class GreetingResource implements HelloApi {

  @Inject GreetingService greetingService;

  @Override
  public String hello() {
    return greetingService.greet();
  }
}
