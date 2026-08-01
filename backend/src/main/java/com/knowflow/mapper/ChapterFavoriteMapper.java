package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ChapterFavorite;
import org.apache.ibatis.annotations.Mapper;

/** 章节收藏 Mapper */
@Mapper
public interface ChapterFavoriteMapper extends BaseMapper<ChapterFavorite> {
}
