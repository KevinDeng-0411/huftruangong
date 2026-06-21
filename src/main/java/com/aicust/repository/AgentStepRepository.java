package com.aicust.repository;

import com.aicust.model.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 任务步骤明细 Repository
 */
@Repository
public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {
    // 方便以后按任务 ID 查询完整的思考链条
    List<AgentStep> findByTaskIdOrderByCreatedAtAsc(String taskId);
}
