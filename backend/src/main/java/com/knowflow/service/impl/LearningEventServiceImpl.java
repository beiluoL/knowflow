package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.LearningEvent;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.service.LearningEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 学习行为事件服务实现（Learning Event System，Phase 1）。
 * <p>
 * 关键：{@code record} 方法运行在独立事务（REQUIRES_NEW）中，且与主业务完全解耦——
 * 即便调用方事务回滚，事件仍会落库；即便事件写入异常，也被吞掉仅告警，绝不外抛。
 */
@Slf4j
@Service
public class LearningEventServiceImpl extends ServiceImpl<LearningEventMapper, LearningEvent> implements LearningEventService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String eventType, String resourceType, Long resourceId, Map<String, Object> metadata) {
        doRecord(userId, eventType, resourceType, resourceId, metadata);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, LearningEventType eventType, String resourceType, Long resourceId, Map<String, Object> metadata) {
        doRecord(userId, eventType == null ? null : eventType.name(), resourceType, resourceId, metadata);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String eventType, String resourceType, Long resourceId) {
        doRecord(userId, eventType, resourceType, resourceId, null);
    }

    private void doRecord(Long userId, String eventType, String resourceType, Long resourceId, Map<String, Object> metadata) {
        try {
            Long uid = userId != null ? userId : SecurityUtils.getCurrentUserIdNullable();
            if (uid == null) {
                return;
            }
            if (eventType == null || eventType.isBlank()) {
                return;
            }
            LearningEvent event = new LearningEvent();
            event.setUserId(uid);
            event.setEventType(eventType);
            event.setResourceType(resourceType);
            event.setResourceId(resourceId);
            event.setMetadata(serialize(metadata));
            this.save(event);
        } catch (Exception ex) {
            log.warn("记录学习事件失败（已忽略，不影响主流程）: type={}, err={}", eventType, ex.getMessage());
        }
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public IPage<LearningEvent> pageEvents(Long userId, String eventType, int current, int size) {
        Page<LearningEvent> page = new Page<>(Math.max(current, 1), Math.max(size, 1));
        LambdaQueryWrapper<LearningEvent> wrapper = new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .orderByDesc(LearningEvent::getCreateTime);
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(LearningEvent::getEventType, eventType);
        }
        return this.page(page, wrapper);
    }
}
