package com.knowflow.store;

import com.knowflow.entity.Order;
import com.knowflow.entity.OrderItem;
import com.knowflow.entity.Product;
import com.knowflow.mapper.OrderItemMapper;
import com.knowflow.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务：创建订单 + 明细（保留购买时价格快照，§62-64/§80/§281）。
 * 订单结构为未来真实支付预留（§42/§108）。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    /** 创建订单（PAID：Coin 已扣，同事务内由 PurchaseService 编排） */
    public Order create(Long userId, Product product, int totalCoin) {
        Order order = new Order();
        order.setOrderNo(genOrderNo());
        order.setUserId(userId);
        order.setTotalCoin(totalCoin);
        order.setStatus("PAID");
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setProductKey(product.getProductKey());
        item.setProductName(product.getName());
        item.setPriceCoin(product.getPriceCoin()); // 购买时价格快照（§43/§80）
        orderItemMapper.insert(item);
        return order;
    }

    private String genOrderNo() {
        return "KF" + LocalDateTime.now().format(NO_FMT) + ThreadLocalRandom.current().nextInt(100, 999);
    }
}
