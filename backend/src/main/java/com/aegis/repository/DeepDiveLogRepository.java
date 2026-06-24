package com.aegis.repository;

import com.aegis.entity.DeepDiveLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeepDiveLogRepository extends JpaRepository<DeepDiveLog, Long> {

    List<DeepDiveLog> findTop20ByNewsIdOrderByCreatedAtDesc(Long newsId);

    List<DeepDiveLog> findTop30ByOrderByCreatedAtDesc();
}
