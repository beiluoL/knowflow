package com.knowflow.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/** 不可变笔记快照，恢复时产生新修订，不删除旧快照。 */
@Data @TableName("learning_note_version")
public class NoteVersion {
 @TableId(type=IdType.AUTO) private Long id;
 private Long noteId;
 private Long userId;
 private Long revision;
 private String title;
 private String content;
    /** 工作台组织字段，仍与正文共享revision。 */
    private Long folderId;
    private Boolean favorite;
    private Long sourceDocId;

 private String tags;
 private String sourceTitle;
 private Long chapterId;
 private Long pathId;
 private String status;
 private Integer noteDeleted;
 private String reason;
 private LocalDateTime createTime;
}
