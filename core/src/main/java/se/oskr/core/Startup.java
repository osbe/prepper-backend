package se.oskr.core;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.oskr.core.domain.User;

@ApplicationScoped
public class Startup {

  @ConfigProperty(name = "app.auth.admin-password")
  String adminPassword;

  @ConfigProperty(name = "app.auth.user-password")
  String userPassword;

  @Transactional
  public void onStart(@Observes StartupEvent evt) {
    User.deleteAll();
    User.add("admin", adminPassword, "admin,user");
    User.add("user", userPassword, "user");
  }
}
