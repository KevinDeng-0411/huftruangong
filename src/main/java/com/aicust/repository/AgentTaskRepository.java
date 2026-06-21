package com.aicust.repository;

import com.aicust.model.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 任务主表 Repository
 */
@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, String> {
}
