package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.lang.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements UserDetails {

	@Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;

	/**
	 * Username of the user for authentication and identification.
	 */
	@Column(nullable = false, unique = true)
	private String username;

	/**
	 * Password of the user.
	 */
	@Column(nullable = false)
	private String password;

	/**
	 * Mail address of the user.
	 * Null if the user did not provide an email address.
	 */
	@Nullable
	private String email;

	/**
	 * The roles of this user.
	 */
	@Setter(AccessLevel.NONE)
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_entity_role",
	                 joinColumns = @JoinColumn(name = "user_id", nullable = false),
	                 uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "user_role"}))
	@Column(name = "user_role", nullable = false)
	@Enumerated(EnumType.STRING)
	private final Set<UserRole> userRoles = new HashSet<>();

	/**
	 * The invitation associated with this user.
	 * Can be null if the user was created without an invitation (e.g., by an admin, or by registering themselves).
	 */
	@OneToOne(mappedBy = "acceptedBy", cascade = CascadeType.ALL, orphanRemoval = true)
	@Nullable
	private UserInvitationEntity invitation;

	/**
	 * The invitations sent by this user.
	 * Mapped by {@link UserInvitationEntity#getInvitedBy()}.
	 */
	@Setter(AccessLevel.NONE)
	@OneToMany(mappedBy = "invitedBy", fetch = FetchType.LAZY, cascade = {})
	private final Set<UserInvitationEntity> invitations = new HashSet<>();

	/**
	 * The projects owned by this user.
	 */
	@Setter(AccessLevel.NONE)
	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private final Set<ProjectEntity> projects = new HashSet<>();

	/**
	 * Checks if this user has the given role.
	 *
	 * @param role The role to check.
	 * @return True if the user has the role, false otherwise.
	 */
	public boolean hasRole(final UserRole role) {
		return userRoles.contains(role);
	}

	/**
	 * Adds the given role to this user.
	 * Does nothing if the user already has the role.
	 *
	 * @param role The role to add.
	 */
	public void addRole(final UserRole role) {
		if (role == null) {
			return;
		}
		userRoles.add(role);
	}

	/**
	 * Removes the given role from this user.
	 * Does nothing if the user does not have the role.
	 *
	 * @param role The role to remove.
	 */
	public void removeRole(final UserRole role) {
		if (role == null) {
			return;
		}
		userRoles.remove(role);
	}

	/**
	 * Replaces all roles of this user with the given roles.
	 *
	 * @param roles The new roles of the user.
	 */
	public void setUserRoles(final Collection<UserRole> roles) {
		userRoles.clear();
		if (roles != null) {
			userRoles.addAll(roles);
		}
	}

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
		return userRoles.stream()
				.map(role -> new SimpleGrantedAuthority(role.name()))
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
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
