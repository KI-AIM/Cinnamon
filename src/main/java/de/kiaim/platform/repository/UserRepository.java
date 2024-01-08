package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.UserEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface UserRepository extends CrudRepository<UserEntity, String> {
}
