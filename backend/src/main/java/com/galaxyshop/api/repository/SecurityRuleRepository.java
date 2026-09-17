package com.galaxyshop.api.repository;

import com.galaxyshop.api.model.SecurityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityRuleRepository extends JpaRepository<SecurityRule, Long> {
    List<SecurityRule> findByEnabledTrueOrderBySortOrderAsc();
}
