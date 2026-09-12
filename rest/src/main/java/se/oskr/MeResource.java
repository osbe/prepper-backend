package se.oskr;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.stream.Collectors;
import se.oskr.api.MeApi;
import se.oskr.model.CurrentUser;

@ApplicationScoped
public class MeResource implements MeApi {

  @Inject SecurityIdentity identity;

  @Override
  @RolesAllowed({"user", "admin"})
  public CurrentUser getCurrentUser() {
    CurrentUser dto = new CurrentUser();
    dto.setUsername(identity.getPrincipal().getName());
    dto.setRoles(identity.getRoles().stream().sorted().collect(Collectors.toList()));
    dto.setCanWrite(identity.hasRole("admin"));
    return dto;
  }
}
