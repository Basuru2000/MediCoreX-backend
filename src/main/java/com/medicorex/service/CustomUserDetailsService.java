package com.medicorex.service;

import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("=== CustomUserDetailsService - Loading user: " + username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    System.out.println("=== CustomUserDetailsService - User not found: " + username);
                    return new UsernameNotFoundException("User not found with username: " + username);
                });

        System.out.println("=== CustomUserDetailsService - User found: " + user.getUsername());
        System.out.println("=== CustomUserDetailsService - User role: " + user.getRole());
        System.out.println("=== CustomUserDetailsService - User active: " + user.getActive());

        return new CustomUserDetails(user);
    }
}