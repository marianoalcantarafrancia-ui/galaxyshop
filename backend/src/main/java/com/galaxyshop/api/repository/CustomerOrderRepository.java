package com.galaxyshop.api.repository;

import com.galaxyshop.api.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    List<CustomerOrder> findByCustomerUsernameOrderByOrderDateDesc(String customerUsername);
    List<CustomerOrder> findByStoreCodeOrderByOrderDateDesc(String storeCode);
}
