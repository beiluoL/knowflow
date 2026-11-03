package com.knowflow.interview.career.discover.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.career.discover.HttpJson;
import com.knowflow.interview.career.discover.JobModels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 适配器字段映射测试：用本地替身替代真实网络，避免测试消耗上游额度。
 *
 * <p>重点验证「上游没有的字段保持 null」，以及 Remote OK 首元素法律声明必须跳过。</p>
 */
class DiscoverAdapterTest {

    private final ObjectMapper json = new ObjectMapper();

    /** 只替换 HTTP 调用，其余解析逻辑完全走真实实现。 */
    private HttpJson stub(String payload) {
        JsonNode node;
        try {
            node = json.readTree(payload);
        } catch (Exception invalid) {
            throw new IllegalStateException(invalid);
        }
        return new HttpJson(json) {
            @Override
            public JsonNode get(String url, String userAgent) {
                return node;
            }
        };
    }

    private JobModels.JobSourcePolicy policy(String id) {
        return new JobModels.JobSourcePolicy(id, true, "doc", "terms", true, "来源：" + id,
                true, 20, 1000L, 60L, false, false, "2026-11-03", "test");
    }

    @Test
    void remotiveMapsFieldsAndKeepsMissingOnesNull() {
        String payload = """
                {"jobs":[
                  {"id":11,"url":"https://remotive.com/jobs/11","title":"Senior Java Engineer",
                   "company_name":"Acme","candidate_required_location":"Worldwide",
                   "salary":"$120k","job_type":"full_time","publication_date":"2026-11-01T08:00:00",
                   "tags":["java","spring"],"description":"<p>Build <b>services</b></p>"},
                  {"id":12,"url":"https://remotive.com/jobs/12","title":"Designer","company_name":"Beta",
                   "description":"design"}
                ]}
                """;
        var adapter = new FeedAdapters.Remotive(stub(payload), json, policy("remotive"));
        List<JobModels.NormalizedJob> jobs = adapter.search(JobModels.JobQuery.of("Java"));

        assertEquals(1, jobs.size(), "关键词过滤应只保留匹配岗位");
        var job = jobs.get(0);
        assertEquals("remotive", job.sourceId());
        assertEquals("11", job.externalId());
        assertEquals("Acme", job.company());
        assertEquals("$120k", job.salaryText());
        assertNull(job.salaryMin(), "上游只给文本时不得推测数值");
        assertNull(job.education(), "上游没有学历字段必须为 null");
        assertEquals("REMOTE", job.remoteType());
        assertEquals("Build services", job.description());
        assertEquals(List.of("java", "spring"), job.tags());
        assertTrue(job.requiredSkills().isEmpty(), "标签不得冒充必备技能");
        assertEquals("来源：remotive", job.attribution());
    }

    @Test
    void remoteOkSkipsLegalNoticeAndParsesSalary() {
        String payload = """
                [
                  {"legal":"API Terms of Service: please link back","last_updated":1},
                  {"id":"99","position":"Backend Engineer","company":"Gamma","location":"Remote",
                   "tags":["java"],"description":"<div>Work</div>","salary_min":90000,"salary_max":120000,
                   "url":"https://remoteok.com/remote-jobs/99","apply_url":"https://gamma.example/apply",
                   "date":"2026-10-30T00:00:00Z"}
                ]
                """;
        var adapter = new FeedAdapters.RemoteOk(stub(payload), json, policy("remoteok"));
        var jobs = adapter.search(JobModels.JobQuery.of("backend"));

        assertEquals(1, jobs.size());
        var job = jobs.get(0);
        assertEquals("99", job.externalId());
        assertEquals(90000, job.salaryMin());
        assertEquals(120000, job.salaryMax());
        assertEquals("USD", job.salaryCurrency());
        assertEquals("https://gamma.example/apply", job.applicationUrl());
    }

    @Test
    void jobicyAndHimalayasMapDefensively() {
        String jobicy = """
                {"jobs":[{"id":7,"url":"https://jobicy.com/jobs/7","jobTitle":"Data Engineer",
                  "companyName":"Delta","jobGeo":"Anywhere","jobLevel":"Senior","jobType":"full_time",
                  "annualSalaryMin":100000,"annualSalaryMax":140000,"salaryCurrency":"USD",
                  "pubDate":"2026-10-29","jobDescription":"<p>ETL</p>","jobIndustry":["data"]}]}
                """;
        var jobicyAdapter = new FeedAdapters.Jobicy(stub(jobicy), json, policy("jobicy"));
        var jobicyJobs = jobicyAdapter.search(JobModels.JobQuery.of("data"));
        assertEquals(1, jobicyJobs.size());
        assertEquals("Delta", jobicyJobs.get(0).company());
        assertEquals(100000, jobicyJobs.get(0).salaryMin());
        assertEquals("Senior", jobicyJobs.get(0).experience());

        String himalayas = """
                {"jobs":[{"guid":"h-1","title":"Platform Engineer","companyName":"Epsilon",
                  "locationRestrictions":["Worldwide"],"minSalary":80000,"maxSalary":110000,
                  "salaryCurrency":"EUR","employmentType":"Full Time","pubDate":"2026-10-28",
                  "applicationLink":"https://himalayas.app/jobs/h-1","description":"<p>K8s</p>"}]}
                """;
        var himalayasAdapter = new FeedAdapters.Himalayas(stub(himalayas), json, policy("himalayas"));
        var himalayasJobs = himalayasAdapter.search(JobModels.JobQuery.of("platform"));
        assertEquals(1, himalayasJobs.size());
        assertEquals("h-1", himalayasJobs.get(0).externalId());
        assertEquals(80000, himalayasJobs.get(0).salaryMin());
        // locationRestrictions 是数组，当前映射不猜测城市文本，必须为 null
        assertNull(himalayasJobs.get(0).location());
    }

    @Test
    void atsAdaptersRequireCompanyTokenAndMapBoards() {
        var greenhouse = new AtsAdapters.Greenhouse(stub("{\"jobs\":[]}"), json, policy("greenhouse"));
        assertFalse(greenhouse.supports(JobModels.JobQuery.of("java")), "没有公司标识时不应启用企业板");

        String payload = """
                {"jobs":[{"id":123,"title":"Staff Engineer","absolute_url":"https://boards.greenhouse.io/acme/jobs/123",
                  "updated_at":"2026-10-27T10:00:00-04:00","location":{"name":"Remote - US"},
                  "content":"&lt;p&gt;Go &amp;amp; Java&lt;/p&gt;"}]}
                """;
        var query = new JobModels.JobQuery("engineer", null, "acme", null, null,
                null, null, null, List.of(), 1, 20);
        var adapter = new AtsAdapters.Greenhouse(stub(payload), json, policy("greenhouse"));
        var jobs = adapter.search(query);
        assertEquals(1, jobs.size());
        assertEquals("acme", jobs.get(0).company());
        assertEquals("Remote - US", jobs.get(0).location());
        assertTrue(jobs.get(0).description().contains("Go"));
    }

    @Test
    void dedupeKeysPreferSourceAndExternalId() {
        var job = new JobModels.NormalizedJob("remotive", "Remotive", "11", "Java", "Acme", "Remote",
                null, null, null, null, null, null, null, null, "REMOTE", "d",
                List.of(), List.of(), List.of(), "https://remotive.com/jobs/11/", null,
                null, null, null, "LIVE");
        assertEquals("ext:remotive:11", job.dedupeKey());

        var noExternal = new JobModels.NormalizedJob("remoteok", "Remote OK", null, "Java", "Acme", "Remote",
                null, null, null, null, null, null, null, null, "REMOTE", "d",
                List.of(), List.of(), List.of(), "https://remoteok.com/x/#frag", null,
                null, null, null, "LIVE");
        assertEquals("url:https://remoteok.com/x", noExternal.dedupeKey());
    }
}
