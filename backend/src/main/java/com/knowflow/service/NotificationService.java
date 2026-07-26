package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.SysNotification;
import com.knowflow.vo.NotificationVO;

/** 消息通知业务服务接口。 */
public interface NotificationService extends IService<SysNotification> {

    IPage<NotificationVO> getNotificationPage(Long userId, String type, Integer isRead, Integer pageNum, Integer pageSize);

    void markAsRead(Long id, Long userId);

    void markAllAsRead(Long userId);

    int getUnreadCount(Long userId);

    void createNotification(Long userId, String type, String title, String content, Long relatedId, String relatedType);
}
