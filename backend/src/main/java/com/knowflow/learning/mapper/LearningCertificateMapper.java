package com.knowflow.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.learning.entity.LearningCertificate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供学习证书的数据访问映射。
 */
@Mapper
public interface LearningCertificateMapper extends BaseMapper<LearningCertificate> {
}
