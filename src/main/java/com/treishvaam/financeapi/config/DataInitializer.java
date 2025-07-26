package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.model.ERole;
import com.treishvaam.financeapi.model.Role;
import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.RoleRepository;
import com.treishvaam.financeapi.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@DependsOn("liquibase")
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Create roles if they don't exist
        if (roleRepository.findByName(ERole.ROLE_ADMIN).isEmpty()) {
            roleRepository.save(new Role(ERole.ROLE_ADMIN));
        }
        if (roleRepository.findByName(ERole.ROLE_USER).isEmpty()) {
            roleRepository.save(new Role(ERole.ROLE_USER));
        }

        // Create admin user if it doesn't exist and assign the admin role
        if (userRepository.findByEmail("callitask@gmail.com").isEmpty()) {
            User adminUser = new User("callitask@gmail.com", passwordEncoder.encode("callitask123"));

            Set<Role> roles = new HashSet<>();
            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Error: Admin role is not found."));
            roles.add(adminRole);

            adminUser.setRoles(roles);
            userRepository.save(adminUser);
            System.out.println("Admin user created successfully with ADMIN role!");
        }
    }
}