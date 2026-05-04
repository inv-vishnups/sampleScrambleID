package com.scimapp.backend.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scimapp.backend.entity.Role;
import com.scimapp.backend.entity.User;
import com.scimapp.backend.repository.RoleRepository;
import com.scimapp.backend.repository.UserRepository;

/**
 * Optional first-time admin for Docker demos when no users exist.
 * Disable in real deployments ({@code APP_COMPOSE_BOOTSTRAP_ADMIN=false}).
 */
@Component
@Profile("docker")
@ConditionalOnProperty(prefix = "app.compose", name = "bootstrap-admin", havingValue = "true")
public class ComposeAdminBootstrap implements ApplicationRunner {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	public ComposeAdminBootstrap(
			UserRepository userRepository,
			RoleRepository roleRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			return;
		}
		Role userRole = roleRepository.findByName("USER").orElseThrow();
		Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
		User admin = new User("admin", "admin@localhost", passwordEncoder.encode("ChangeMe!123"));
		Set<Role> roles = new HashSet<>();
		roles.add(userRole);
		roles.add(adminRole);
		admin.setRoles(roles);
		userRepository.save(admin);
	}
}
