package site.arookieofc.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.dao.entity.PendingBatchImportRecord;

import java.util.List;

@Mapper
public interface PendingBatchImportMapper {

    // 主表操作
    PendingBatchImport getById(@Param("id") String id);

    List<PendingBatchImport> listAll();

    List<PendingBatchImport> listByStatus(@Param("status") String status);

    List<PendingBatchImport> listBySubmitter(@Param("submittedBy") String submittedBy);

    int insert(PendingBatchImport pendingBatchImport);

    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("reviewedAt") java.time.LocalDateTime reviewedAt,
                     @Param("reviewedBy") String reviewedBy,
                     @Param("rejectedReason") String rejectedReason);

    int delete(@Param("id") String id);

    // 分页查询
    List<PendingBatchImport> listPaged(@Param("status") String status,
                                       @Param("submittedBy") String submittedBy,
                                       @Param("pageSize") int pageSize,
                                       @Param("offset") int offset);

    int countFiltered(@Param("status") String status, @Param("submittedBy") String submittedBy);

    // 详情表操作
    List<PendingBatchImportRecord> getRecordsByBatchId(@Param("batchId") String batchId);

    int insertRecord(PendingBatchImportRecord record);

    int insertRecords(@Param("records") List<PendingBatchImportRecord> records);

    int deleteRecordsByBatchId(@Param("batchId") String batchId);
}

