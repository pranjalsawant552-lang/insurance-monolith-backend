package com.insurance.auth.security;

import com.insurance.auth.entity.User;
import com.insurance.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = identifier.contains("@")
                ? userRepository.findByEmailAndDeletedFalse(identifier)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier))
                : userRepository.findByMobileNumberAndDeletedFalse(identifier)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId().toString())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .accountLocked(user.isLocked())
                .disabled(!user.isActive())
                .build();
    }
}
