package com.knowflow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.Product;
import com.knowflow.mapper.ProductMapper;
import com.knowflow.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品服务：查询商品、读取价格、验证商品状态（§54-55）。
 * 价格为服务端唯一可信来源（§55/§103）。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final EntitlementService entitlementService;

    /** 商品列表（含当前用户是否拥有 owned，§58/§284：拥有判断来自 Entitlement，不用 coin>=price） */
    public List<ProductVO> listProducts(String type, Long userId) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>()
                .orderByAsc(Product::getId);
        if (type != null && !type.isBlank()) {
            qw.eq(Product::getType, type.toUpperCase());
        }
        List<Product> products = productMapper.selectList(qw);
        Set<Long> owned = entitlementService.ownedProductIds(userId);
        return products.stream().map(p -> toVO(p, owned.contains(p.getId()))).collect(Collectors.toList());
    }

    /** 商品详情（含 owned） */
    public ProductVO getProductVO(Long id, Long userId) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            return null;
        }
        return toVO(p, entitlementService.hasActive(userId, id));
    }

    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }

    private ProductVO toVO(Product p, boolean owned) {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setProductKey(p.getProductKey());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setType(p.getType());
        vo.setAppKey(p.getAppKey());
        vo.setPriceCoin(p.getPriceCoin());
        vo.setStatus(p.getStatus());
        vo.setVersion(p.getVersion());
        vo.setOwned(owned);
        return vo;
    }
}
