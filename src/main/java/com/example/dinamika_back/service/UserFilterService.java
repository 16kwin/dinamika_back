// UserFilterService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.UserFilterDTO;
import com.example.dinamika_back.model.User;
import com.example.dinamika_back.model.UserFilter;
import com.example.dinamika_back.repository.UserFilterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserFilterService {

    private final UserFilterRepository userFilterRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserFilterService(UserFilterRepository userFilterRepository, 
                             UserService userService,
                             ObjectMapper objectMapper) {
        this.userFilterRepository = userFilterRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public UserFilterDTO getUserFilters(String username) {
        Optional<User> userOpt = userService.getUserByUsername(username);
        if (userOpt.isEmpty()) {
            return new UserFilterDTO();
        }
        
        User user = userOpt.get();
        Optional<UserFilter> filterOpt = userFilterRepository.findByUserId(user.getId());
        
        if (filterOpt.isEmpty()) {
            return new UserFilterDTO();
        }
        
        return objectMapper.convertValue(filterOpt.get().getFilterData(), UserFilterDTO.class);
    }

    @Transactional
    public void saveUserFilters(String username, UserFilterDTO filterDTO) {
        Optional<User> userOpt = userService.getUserByUsername(username);
        if (userOpt.isEmpty()) {
            return;
        }
        
        User user = userOpt.get();
        Optional<UserFilter> existingFilter = userFilterRepository.findByUserId(user.getId());
        
        Map<String, Object> filterData = objectMapper.convertValue(filterDTO, Map.class);
        
        if (existingFilter.isPresent()) {
            UserFilter userFilter = existingFilter.get();
            userFilter.setFilterData(filterData);
            userFilterRepository.save(userFilter);
        } else {
            UserFilter userFilter = new UserFilter();
            userFilter.setUser(user);
            userFilter.setFilterData(filterData);
            userFilterRepository.save(userFilter);
        }
    }

    @Transactional
    public void deleteUserFilters(String username) {
        Optional<User> userOpt = userService.getUserByUsername(username);
        if (userOpt.isPresent()) {
            userFilterRepository.deleteByUserId(userOpt.get().getId());
        }
    }
}