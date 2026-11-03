package com.knowflow.interview.career.discover;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 岗位发现 API。
 *
 * <p>所有读取都以服务端 userId 为边界；搜索本身不写库，只有「加入岗位中心」会创建本人记录。</p>
 */
@RestController
@RequestMapping("/api/interview/career/discover")
@RequiredArgsConstructor
public class CareerDiscoverController {

    private final JobSearchService search;
    private final JobSourceRegistry registry;
    private final CareerDiscoverService service;

    public record HistoryRequest(String keyword, Map<String, Object> filters, Boolean saved) { }

    @GetMapping("/sources")
    public Result<?> sources() {
        return Result.success(registry.describe());
    }

    @GetMapping("/jobs")
    public Result<?> jobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) Boolean remoteOnly,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String sources,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<String> sourceIds = sources == null || sources.isBlank()
                ? List.of()
                : Arrays.stream(sources.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        JobModels.JobQuery query = new JobModels.JobQuery(
                keyword, location, company, remoteOnly, salaryMin, experience, education, jobType,
                sourceIds, page, size);
        return Result.success(search.search(query));
    }

    @GetMapping("/history")
    public Result<?> history() {
        return Result.success(service.history(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/history")
    public Result<?> addHistory(@RequestBody HistoryRequest body) {
        if (body == null) throw new BusinessException(400, "缺少搜索条件");
        return Result.success(service.addHistory(SecurityUtils.getCurrentUserId(), body.keyword(),
                body.filters(), Boolean.TRUE.equals(body.saved())));
    }

    @DeleteMapping("/history/{id}")
    public Result<?> removeHistory(@PathVariable long id) {
        service.removeHistory(SecurityUtils.getCurrentUserId(), id);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<?> importJob(@RequestBody CareerDiscoverService.ImportRequest body) {
        if (body == null) throw new BusinessException(400, "缺少岗位信息");
        return Result.success(service.importJob(SecurityUtils.getCurrentUserId(), body));
    }

    @GetMapping("/jobs/{recordId}/source")
    public Result<?> jobSource(@PathVariable long recordId) {
        return Result.success(service.jobSource(SecurityUtils.getCurrentUserId(), recordId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> error(BusinessException error) {
        int code = error.getCode();
        if (code < 400 || code > 599) code = 500;
        return ResponseEntity.status(code).body(Result.error(code, error.getMessage()));
    }
}
