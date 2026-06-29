package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.lang.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements UserDetails {

	@Id
	private String email;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private final UserRole userRole = UserRole.ROLE_USER;

	/**
	 * The projects owned by this user.
	 */
	@Setter(AccessLevel.NONE)
	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private final Set<ProjectEntity> projects = new HashSet<>();

	@Nullable
	public ProjectEntity getProject(final UUID projectId) {
		return projects.stream()
				.filter(project -> project.getExternalId().equals(projectId))
				.findFirst()
				.orElse(null);
	}

	public void addProject(final ProjectEntity project) {
		if (project == null || projects.contains(project)) {
			return;
		}
		projects.add(project);
		if (project.getUser() != this) {
			project.setUser(this);
		}
	}

	public void removeProject(final ProjectEntity project) {
		if (project == null || !projects.contains(project)) {
			return;
		}
		projects.remove(project);
		if (project.getUser() == this) {
			project.setUser(null);
		}
	}

	//==============================
	// Implementation of UserDetails
	//==============================

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		SimpleGrantedAuthority authority = new SimpleGrantedAuthority(userRole.name());
		return Collections.singletonList(authority);
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
