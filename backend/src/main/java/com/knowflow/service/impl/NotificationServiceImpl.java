package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.entity.SysNotification;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysNotificationMapper;
import com.knowflow.service.NotificationService;
import com.knowflow.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<SysNotificationMapper, SysNotification> implements NotificationService {

    @Override
    public IPage<NotificationVO> getNotificationPage(Long userId, String type, Integer isRead, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(SysNotification::getUserId, userId);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(SysNotification::getType, type);
        }
        if (isRead != null) {
            wrapper.eq(SysNotification::getIsRead, isRead);
        }
        wrapper.orderByDesc(SysNotification::getCreateTime);

        Page<SysNotification> page = this.page(new Page<>(com.knowflow.common.PageQuery.normalizePageNum(pageNum), com.knowflow.common.PageQuery.normalizePageSize(pageSize)), wrapper);
        return page.convert(n -> BeanUtil.copyProperties(n, NotificationVO.class));
    }

    @Override
    public void markAsRead(Long id, Long userId) {
        SysNotification notification = this.getById(id);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        if (!java.util.Objects.equals(notification.getUserId(), userId)) {
            throw new BusinessException("无权操作该通知");
        }
        notification.setIsRead(1);
        this.updateById(notification);
    }

    @Override
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0);
        SysNotification update = new SysNotification();
        update.setIsRead(1);
        this.update(update, wrapper);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0)));
    }

    @Override
    public void createNotification(Long userId, String type, String title, String content, Long relatedId, String relatedType) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setRelatedId(relatedId);
        notification.setRelatedType(relatedType);
        this.save(notification);
    }
}
