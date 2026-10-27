package com.knowflow.catalog;
import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;
/** 来源 ID 命名空间，不等同于物理迁移后的主键。缺失字段保持 null。 */
@Data
public class QuestionCatalogItem {
 private String editToken;
 private String catalogId,sourceType,canonicalKey,questionType,title,reviewStatus,editorPath;
 private Long sourceQuestionId,categoryId,sourceStorageId;
 private Integer difficulty,sourceDifficulty,status,sourceVersion;
 private List<Long> knowledgePointIds;
 private List<String> knowledgePointCodes,tags,capabilities,restrictions;
 private LocalDateTime createdAt,updatedAt;
 private boolean canonicalEvidence;
 private long duplicateCount;
}
