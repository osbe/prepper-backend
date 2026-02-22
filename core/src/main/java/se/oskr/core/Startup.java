package se.oskr.core;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import se.oskr.core.domain.User;

@ApplicationScoped
public class Startup {

  @Transactional
  public void onStart(@Observes StartupEvent evt) {
    User.deleteAll();
    User.add("admin", "admin", "admin,user");
    User.add("user", "user", "user");
  }
}
