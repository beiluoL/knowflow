package com.knowflow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.Entitlement;
import com.knowflow.entity.Product;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.EntitlementMapper;
import com.knowflow.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资格服务：查询资格、授予资格（§163/§204）。
 * Entitlement = 拥有资格；UserApp = 启用状态（§45/§87），二者互不影响。
 * 唯一约束（user_id, product_id）保证同一商品只有一个 ACTIVE 资格（§61/§282）。
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final EntitlementMapper entitlementMapper;

    public boolean hasActive(Long userId, Long productId) {
        return entitlementMapper.selectCount(new LambdaQueryWrapper<Entitlement>()
                .eq(Entitlement::getUserId, userId)
                .eq(Entitlement::getProductId, productId)
                .eq(Entitlement::getStatus, "ACTIVE")) > 0;
    }

    /** 我拥有的商品 ID 集合（用于商品列表 owned 标记） */
    public Set<Long> ownedProductIds(Long userId) {
        return entitlementMapper.selectList(new LambdaQueryWrapper<Entitlement>()
                        .eq(Entitlement::getUserId, userId)
                        .eq(Entitlement::getStatus, "ACTIVE"))
                .stream().map(Entitlement::getProductId).collect(Collectors.toSet());
    }

    /** 我拥有的商品列表 */
    public List<ProductVO> listOwned(Long userId) {
        return entitlementMapper.selectList(new LambdaQueryWrapper<Entitlement>()
                        .eq(Entitlement::getUserId, userId)
                        .eq(Entitlement::getStatus, "ACTIVE")
                        .orderByDesc(Entitlement::getGrantedAt))
                .stream().map(e -> {
                    ProductVO vo = new ProductVO();
                    vo.setProductKey(e.getProductKey());
                    vo.setOwned(true);
                    return vo;
                }).collect(Collectors.toList());
    }

    /** 授予资格（购买事务内调用；已拥有则抛 ALREADY_OWNED，§71/§94） */
    public void grant(Long userId, Product product) {
        if (hasActive(userId, product.getId())) {
            throw new BusinessException(EconomyError.ALREADY_OWNED, "该应用已拥有");
        }
        Entitlement e = new Entitlement();
        e.setUserId(userId);
        e.setProductId(product.getId());
        e.setProductKey(product.getProductKey());
        e.setStatus("ACTIVE");
        e.setGrantedAt(LocalDateTime.now());
        entitlementMapper.insert(e);
    }
}
