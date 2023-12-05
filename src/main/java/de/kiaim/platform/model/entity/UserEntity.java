package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.UserRole;
import jakarta.persistence.*;
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
public class UserEntity implements UserDetails {

	@Id
	private String email;

	private String password;

	private final UserRole userRole = UserRole.USER;

	@Nullable
	@OneToOne(optional = true, fetch = FetchType.LAZY, orphanRemoval = false, cascade = CascadeType.ALL)
	@JoinColumn(name = "data_configuration_id", referencedColumnName = "email")
	private DataConfigurationEntity dataConfiguration = null;

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
