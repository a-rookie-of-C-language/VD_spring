package site.arookieofc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.arookieofc.controller.VO.ActivityVO;
import site.arookieofc.controller.VO.ActivityPageVO;
import site.arookieofc.controller.VO.Result;
import site.arookieofc.service.ActivityService;
import site.arookieofc.service.BO.ActivityStatus;
import site.arookieofc.service.BO.ActivityType;
import site.arookieofc.service.dto.ActivityDTO;
import io.jsonwebtoken.Claims;
import site.arookieofc.util.JWTUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityService activityService;

    @GetMapping
    public Result list(@RequestParam(value = "page", required = false, defaultValue = "1") int page,
                       @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
                       @RequestParam(value = "type", required = false) ActivityType type,
                       @RequestParam(value = "status", required = false) ActivityStatus status,
                       @RequestParam(value = "functionary", required = false) String functionary,
                       @RequestParam(value = "name", required = false) String name,
                       @RequestParam(value = "startFrom", required = false) String startFrom,
                       @RequestParam(value = "startTo", required = false) String startTo,
                       @RequestParam(value = "isFull", required = false) Boolean isFull) {
        OffsetDateTime sf = startFrom == null || startFrom.isEmpty() ? null : OffsetDateTime.parse(startFrom);
        OffsetDateTime st = startTo == null || startTo.isEmpty() ? null : OffsetDateTime.parse(startTo);
        String role = null;
        String studentNo = null;
        try {
            String auth = RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes
                    ? ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader("Authorization")
                    : null;
            if (auth != null && auth.startsWith("Bearer ")) {
                Claims claims = JWTUtils.parseToken(auth.substring(7));
                role = claims.get("role", String.class);
                studentNo = claims.getSubject();
            }
        } catch (Exception ignored) {
        }
        boolean useAll = status != null || (("admin".equals(role) || "superAdmin".equals(role))) || (role != null && "functionary".equals(role) && functionary != null && functionary.equals(studentNo));
        int total = useAll ? activityService.countActivitiesAll(type, status, functionary, name, sf, st, isFull)
                : activityService.countActivities(type, status, functionary, name, sf, st, isFull);
        List<ActivityDTO> dtos = useAll ? activityService.listActivitiesPagedAll(type, status, functionary, name, sf, st, isFull, page, pageSize)
                : activityService.listActivitiesPaged(type, status, functionary, name, sf, st, isFull, page, pageSize);
        List<ActivityVO> items = dtos.stream().map(ActivityVO::fromDTO).collect(java.util.stream.Collectors.toList());
        ActivityPageVO data = ActivityPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
        return Result.success(data);
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public Result create(@ModelAttribute ActivityDTO dto) {
        ActivityDTO created = activityService.createActivity(dto);
        return Result.success(ActivityVO.fromDTO(created));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public Result update(@PathVariable("id") String id,
                         @ModelAttribute ActivityDTO dto) {
        ActivityDTO updated = activityService.updateActivity(id, dto);
        return Result.success(ActivityVO.fromDTO(updated));
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") String id) {
        activityService.deleteActivity(id);
        return Result.success();
    }

    @PostMapping("/{id}/enroll")
    public Result enroll(@PathVariable("id") String id,
                         @RequestParam("studentNo") String studentNo) {
        String code = activityService.enroll(id, studentNo);
        if ("OK".equals(code)) {
            return Result.success();
        }
        if ("NOT_FOUND".equals(code)) {
            return Result.of(404, "NOT_FOUND", null);
        }
        if ("CAPACITY_FULL".equals(code)) {
            return Result.of(409, "CAPACITY_FULL", null);
        }
        if ("ALREADY_ENROLLED".equals(code)) {
            return Result.of(409, "ALREADY_ENROLLED", null);
        }
        return Result.error("UNKNOWN_ERROR");
    }

    @PostMapping("/{id}/review")
    public Result review(@PathVariable("id") String id,
                         @RequestParam("approve") boolean approve) {
        String role = null;
        try {
            String auth = RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes
                    ? ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest()
                    .getHeader("Authorization")
                    : null;
            if (auth != null && auth.startsWith("Bearer ")) {
                Claims claims = JWTUtils.parseToken(auth.substring(7));
                role = claims.get("role", String.class);
            }
        } catch (Exception ignored) {}
        if (role == null || !("admin".equals(role) || "superAdmin".equals(role))) {
            return Result.of(401, "UNAUTHORIZED", null);
        }
        try {
            ActivityDTO dto = activityService.reviewActivity(id, approve);
            return Result.success(ActivityVO.fromDTO(dto));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("NOT_FOUND".equals(msg)) {
                return Result.of(404, "NOT_FOUND", null);
            }
            if ("INVALID_TIME".equals(msg) || "ENROLLMENT_PASSED".equals(msg)) {
                return Result.of(400, msg, null);
            }
            return Result.error(msg);
        }
    }

    @GetMapping("/MyActivities")
    public Result getMyActivities(@RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize
                                  ) {
        String studentNo = null;
        try {
            String auth = RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes
                    ? ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest()
                    .getHeader("Authorization")
                    : null;
            Claims claims = null;
            if (auth != null) {
                claims = JWTUtils.parseToken(auth.substring(7));
            }
            if (claims != null) {
                studentNo = claims.getSubject();
            }
        } catch (Exception ignored) {
        }
        if (studentNo == null || studentNo.isEmpty()) {
            return Result.of(401, "UNAUTHORIZED", null);
        }
        int total = activityService.countActivitiesAll(null, null, studentNo, null, null, null, null);
        List<ActivityDTO> dtos = activityService.listActivitiesPagedAll(null, null, studentNo, null, null, null, null, page, pageSize);
        List<ActivityVO> items = dtos.stream().map(ActivityVO::fromDTO).collect(java.util.stream.Collectors.toList());
        ActivityPageVO data = ActivityPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
        return Result.success(data);
    }
}
