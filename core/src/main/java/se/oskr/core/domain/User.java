package se.oskr.core.domain;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

  @Column(unique = true, nullable = false)
  public String username;

  @Column(nullable = false)
  public String password;

  @Column(nullable = false)
  public String role;

  public static void add(String username, String password, String role) {
    User user = new User();
    user.username = username;
    user.password = BcryptUtil.bcryptHash(password);
    user.role = role;
    user.persist();
  }
}
