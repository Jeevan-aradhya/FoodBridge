package com.foodbridge.repository;

import com.foodbridge.entity.User;
import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    List<User> findByRole(Role role);

    List<User> findTop50ByRoleAndStatusAndCityIgnoreCase(Role role, AccountStatus status, String city);

    @Query("select u.status from User u where u.id = :id")
    Optional<AccountStatus> findStatusById(@Param("id") Long id);
}
