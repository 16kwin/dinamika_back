package com.example.dinamika_back.service;

import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.multipart.MultipartFile;
import com.example.dinamika_back.model.Role;
import com.example.dinamika_back.model.User;
import com.example.dinamika_back.repository.RoleRepository;
import com.example.dinamika_back.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private UserRepository userRepository;

    private RoleRepository roleRepository;

    private PasswordEncoder passwordEncoder;


    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    public void setRoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Autowired
    public void setRoleService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAllByOrderById();
    }


    public Optional<User> findByUsername(String username) {
        return userRepository.findTop1ByUsername(username);
    }

    public String getRole() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            String username = userDetails.getUsername();
            return userDetails.getAuthorities().iterator().next().getAuthority().toString();
        } else {
            return "no role";
        }
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id).orElse(null);
    }

    public String getRussianRole(String rolename) {
        return switch (rolename) {
            case "ROLE_VIEWER" -> {
                rolename = "Просмотр";
                yield rolename;
            }
            case "ROLE_OPERATOR" -> {
                rolename = "Оператор";
                yield rolename;
            }
            case "ROLE_ADMIN" -> {
                rolename = "Администратор";
                yield rolename;
            }

            default -> {
                rolename = "Неизвестно";
                yield rolename;
            }
        };
    }

    public String getRoleText() {

        if (!getRole().equals("no role")) {
            return getRole();
        } else return "no role";
    }

    public String getIconRoleText() {
        if (!getRole().equals("no role")) {
            String iconRoleText = getRole();
            if (iconRoleText.contains(" ")) {
                return iconRoleText.substring(0, 1).toUpperCase() + iconRoleText.substring(iconRoleText.indexOf(" ") + 1, iconRoleText.indexOf(" ") + 2).toUpperCase();
            } else {
                return iconRoleText.substring(0, 1).toUpperCase();
            }
        } else return "no role";
    }


    public Optional<User> getUserByUsername(String username) {
        return userRepository.findTop1ByUsername(username);
    }

    public Optional<User> getUserById(Integer id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByUsername();
    }

    @Transactional
    public Integer newUser(User user) throws Exception {
        User newUser = userRepository.save(user);
        return newUser.getId();
    }

    @Transactional
    public void updateUser(User user) {
        userRepository.save(user);
    }


    @Transactional
    public void deleteUserById(Integer id) {
        userRepository.deleteById(id);
    }


}
