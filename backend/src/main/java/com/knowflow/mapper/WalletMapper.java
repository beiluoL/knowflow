package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;

/** Wallet 表数据访问层。 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {
}
