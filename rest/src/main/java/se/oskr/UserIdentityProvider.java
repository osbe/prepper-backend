package se.oskr;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import se.oskr.core.domain.User;
import se.oskr.core.service.UserService;

@ApplicationScoped
public class UserIdentityProvider
    implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

  @Inject UserService userService;

  @Override
  public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
    return UsernamePasswordAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      UsernamePasswordAuthenticationRequest request, AuthenticationRequestContext context) {
    return context.runBlocking(
        () -> {
          User user = userService.findByUsername(request.getUsername());
          if (user == null
              || !BcryptUtil.matches(
                  new String(request.getPassword().getPassword()), user.password)) {
            throw new AuthenticationFailedException();
          }
          return QuarkusSecurityIdentity.builder()
              .setPrincipal(new QuarkusPrincipal(request.getUsername()))
              .addRoles(new HashSet<>(Arrays.asList(user.role.split(","))))
              .build();
        });
  }
}
