package site.arookieofc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.arookieofc.controller.VO.ActivityVO;
import site.arookieofc.controller.VO.MyActivityItemVO;
import site.arookieofc.controller.VO.MyActivityPageVO;
import site.arookieofc.dao.entity.PendingBatchImport;
import site.arookieofc.service.dto.ActivityDTO;
import site.arookieofc.service.dto.PendingActivityDTO;
import site.arookieofc.service.dto.UserDTO;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyActivityService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ActivityService activityService;
    private final PendingActivityService pendingActivityService;
    private final BatchImportService batchImportService;
    private final UserService userService;

    public MyActivityPageVO getMyActivities(String studentNo, int page, int pageSize) {
        List<MyActivityItemVO> allItems = new ArrayList<>();

        List<ActivityDTO> activityDtos = activityService
                .listActivitiesPagedAll(null, null, studentNo, null, null, null, null, 1, Integer.MAX_VALUE);
        allItems.addAll(activityDtos.stream()
                .map(ActivityVO::fromDTO)
                .map(MyActivityItemVO::fromActivityVO)
                .collect(Collectors.toList()));

        List<PendingActivityDTO> pendingActivities = pendingActivityService.listPendingActivitiesPaged(
                null, null, null, studentNo, 1, Integer.MAX_VALUE);
        allItems.addAll(pendingActivities.stream()
                .map(MyActivityItemVO::fromPendingActivityDTO)
                .collect(Collectors.toList()));

        List<PendingBatchImport> pendingBatchImports = batchImportService.getPendingBatchImportsBySubmitter(studentNo);
        allItems.addAll(pendingBatchImports.stream()
                .filter(bi -> "PENDING".equals(bi.getStatus()) || "REJECTED".equals(bi.getStatus()))
                .map(bi -> MyActivityItemVO.fromPendingBatchImport(bi, ZONE))
                .collect(Collectors.toList()));

        allItems.sort((a, b) -> {
            OffsetDateTime timeA = a.getCreatedAt() != null ? a.getCreatedAt() :
                    (a.getStartTime() != null ? a.getStartTime() : OffsetDateTime.MIN);
            OffsetDateTime timeB = b.getCreatedAt() != null ? b.getCreatedAt() :
                    (b.getStartTime() != null ? b.getStartTime() : OffsetDateTime.MIN);
            return timeB.compareTo(timeA);
        });

        int total = allItems.size();
        int startIndex = Math.max(0, (page - 1) * pageSize);
        int endIndex = Math.min(startIndex + pageSize, total);
        List<MyActivityItemVO> pagedItems = startIndex < total
                ? allItems.subList(startIndex, endIndex)
                : new ArrayList<>();

        return MyActivityPageVO.builder()
                .items(pagedItems)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    public Map<String, Object> getMyStatus(String studentNo) {
        List<ActivityDTO> participatedActivities = activityService.getActivitiesByStudentNo(studentNo);
        double totalDuration = userService.getUserByStudentNo(studentNo)
                .map(UserDTO::getTotalHours)
                .orElse(0.0);

        List<ActivityVO> activityList = participatedActivities.stream()
                .map(ActivityVO::fromDTO)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("totalDuration", totalDuration);
        data.put("totalActivities", participatedActivities.size());
        data.put("activities", activityList);
        return data;
    }
}
