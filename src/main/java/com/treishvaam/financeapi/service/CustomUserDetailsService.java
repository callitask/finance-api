package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(), // Use getEmail()
            user.getPassword(), // Use getPassword()
            user.isEnabled(),
            true,
            true,
            true,
            user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name())) // Corrected this line to get the string name of the ERole enum
                .collect(Collectors.toList())
        );
    }
}