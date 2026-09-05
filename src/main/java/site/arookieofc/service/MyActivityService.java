package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.arookieofc.controller.VO.ActivityVO;
import site.arookieofc.controller.VO.MyActivityItemVO;
import site.arookieofc.controller.VO.MyActivityPageVO;
import site.arookieofc.dao.mapper.MyActivityMapper;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.dto.UserDTO;
import site.arookieofc.util.PaginationUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyActivityService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 50;

    private final MyActivityMapper myActivityMapper;
    private final ActivityService activityService;
    private final UserService userService;

    /**
     * Uses a single SQL UNION ALL query instead of 8 separate queries + memory sort.
     */
    public MyActivityPageVO getMyActivities(String studentNo, int page, int pageSize) {
        int safePageSize = PaginationUtils.normalizePageSize(pageSize, MAX_PAGE_SIZE);
        int safePage = PaginationUtils.normalizePage(page);
        int offset = PaginationUtils.offset(safePage, safePageSize);

        int total = myActivityMapper.countMyActivities(studentNo);
        List<Map<String, Object>> rows = myActivityMapper.listMyActivitiesPaged(studentNo, safePageSize, offset);

        List<MyActivityItemVO> items = rows.stream()
                .map(this::mapToItemVO)
                .collect(Collectors.toList());

        return MyActivityPageVO.builder()
                .items(items).total(total).page(safePage).pageSize(safePageSize).build();
    }

    public Map<String, Object> getMyStatus(String studentNo) {
        List<ActivityDTO> participatedActivities = activityService.getActivitiesByStudentNo(studentNo);
        double totalDuration = userService.getUserByStudentNo(studentNo)
                .map(UserDTO::getTotalHours).orElse(0.0);

        Map<String, Object> data = new HashMap<>();
        data.put("totalDuration", totalDuration);
        data.put("totalActivities", participatedActivities.size());
        data.put("activities", participatedActivities.stream().map(ActivityVO::fromDTO).collect(Collectors.toList()));
        return data;
    }

    private MyActivityItemVO mapToItemVO(Map<String, Object> row) {
        String source = (String) row.get("source");
        LocalDateTime createdAt = toLocalDateTime(row.get("created_at"));
        LocalDateTime startTime = toLocalDateTime(row.get("start_time"));
        LocalDateTime reviewedAt = toLocalDateTime(row.get("reviewed_at"));

        MyActivityItemVO.MyActivityItemVOBuilder b = MyActivityItemVO.builder()
                .id((String) row.get("id"))
                .itemType(source)
                .name((String) row.get("name"))
                .description((String) row.get("description"))
                .functionary((String) row.get("functionary"))
                .submittedBy((String) row.get("submitted_by"))
                .duration(toDouble(row.get("duration")))
                .coverPath((String) row.get("cover_path"))
                .rejectedReason((String) row.get("rejected_reason"))
                .reviewedBy((String) row.get("reviewed_by"))
                .createdAt(toOffset(createdAt))
                .startTime(toOffset(startTime))
                .reviewedAt(toOffset(reviewedAt));

        if ("ACTIVITY".equals(source)) {
            b.status(toEnum(ActivityStatus.class, row.get("status")))
             .activityType(toEnum(ActivityType.class, row.get("type")))
             .isFull(toBoolean(row.get("is_full")))
             .maxParticipants(toInt(row.get("max_participants")));
        } else if ("PENDING_ACTIVITY".equals(source)) {
            b.status(toEnum(ActivityStatus.class, row.get("status")))
             .activityType(toEnum(ActivityType.class, row.get("type")))
             .batchStatus("PENDING");
        } else if ("BATCH_IMPORT".equals(source)) {
            b.batchStatus((String) row.get("status"));
        }
        return b.build();
    }

    private static LocalDateTime toLocalDateTime(Object val) {
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private static OffsetDateTime toOffset(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(ZONE).toOffsetDateTime();
    }

    private static Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }

    private static Integer toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    private static Boolean toBoolean(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() != 0;
        return null;
    }

    private static <E extends Enum<E>> E toEnum(Class<E> enumType, Object val) {
        if (val == null) return null;
        String normalized = val.toString().trim();
        if (normalized.isEmpty()) return null;
        try {
            return Enum.valueOf(enumType, normalized);
        } catch (IllegalArgumentException e) {
            for (E enumValue : enumType.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(normalized)) {
                    return enumValue;
                }
            }
        }
        return null;
    }
}
