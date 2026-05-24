package cn.wildfirechat.org.secondary.jpa;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPasswordRepository extends CrudRepository<UserPassword, String> {
}
