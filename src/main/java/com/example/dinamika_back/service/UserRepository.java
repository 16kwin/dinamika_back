package com.example.dinamika_back.service;

import com.example.dinamika_back.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findTop1ByUsername(String username);

    List<User> findAllByOrderByUsername();
}