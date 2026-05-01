package com.yogesh.security;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		String loginId = username == null ? "" : username.trim();
		if (loginId.isBlank()) {
			throw new UsernameNotFoundException("User not found");
		}

		User user = userRepository.findByEmailIgnoreCase(loginId)
				.or(() -> userRepository.findByUsernameIgnoreCase(loginId))
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));

		return new CustomUserDetails(user);
	}
}
