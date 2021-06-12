package com.sixj.repository;

import com.sixj.entry.COrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author sixiaojie
 * @date 2021-06-12-15:11
 */
public interface COrderRepository extends JpaRepository<COrder, Long> {
}
