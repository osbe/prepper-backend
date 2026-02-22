package se.oskr.core.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import se.oskr.core.domain.User;

@ApplicationScoped
public class UserService {

  @Transactional
  public User findByUsername(String username) {
    return User.find("username", username).firstResult();
  }
}
