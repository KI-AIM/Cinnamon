package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true)
public interface UserRepository extends CrudRepository<UserEntity, String> {

	@EntityGraph(attributePaths = {"project", "project.projectConfiguration"})
	Optional<UserEntity> findByEmail(String email);
}
