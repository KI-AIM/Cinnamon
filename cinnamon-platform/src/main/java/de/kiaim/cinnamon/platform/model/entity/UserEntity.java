package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

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
	private final UserRole userRole = UserRole.ROLE_USER;

	@Nullable
	@OneToOne(optional = true, fetch = FetchType.LAZY, orphanRemoval = false, cascade = CascadeType.ALL)
	@JoinColumn(name = "project_id", referencedColumnName = "id")
	private ProjectEntity project = null;

	/**
	 * Links the given project with this user.
	 * @param newProject The project to link.
	 */
	public void setProject(@Nullable final ProjectEntity newProject) {
		final ProjectEntity oldProject = this.project;
		this.project = newProject;
		if (oldProject != null && oldProject.getUser() == this) {
			oldProject.setUser(null);
		}
		if (newProject != null && newProject.getUser() != this) {
			newProject.setUser(this);
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
