package org.example.districtpulse;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.example.districtpulse.news.*;
import org.example.districtpulse.incident.*;
import org.example.districtpulse.source.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformIntegrationTests {
    @LocalServerPort int port;
    @Autowired NewsArticleRepository articles;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentService grouping;
    @Autowired SecretBox secrets;
    @Autowired UrlGuard urls;
    private final HttpClient client=HttpClient.newHttpClient();private final JsonMapper mapper=JsonMapper.builder().build();
    HttpResponse<String> request(String method,String path,Object body,String token) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Content-Type","application/json");if(token!=null)req.header("Authorization","Bearer "+token);
        req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));return client.send(req.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode json(HttpResponse<String> r){return mapper.readTree(r.body());}
    String login(String email,String password) throws Exception {var r=request("POST","/auth/login",Map.of("email",email,"password",password),null);assertEquals(200,r.statusCode(),r.body());return json(r).path("token").asText();}
    String admin() throws Exception {return login("admin@tests.invalid","Test-only-password-2026");}
    @Test void anonymousAndForgedTokensCannotAccessData() throws Exception {
        assertEquals(401,request("GET","/sources",null,null).statusCode());assertEquals(401,request("GET","/sources",null,"fake-token").statusCode());
        assertEquals(401,request("POST","/auth/login",Map.of("email","admin@tests.invalid","password","wrong-password"),null).statusCode());
    }
    @Test void realLoginPreferencesActionsCommentsReportsAndDepartmentScope() throws Exception {
        String token=admin();assertEquals("administrator",json(request("GET","/auth/me",null,token)).path("role").asText());
        var prefs=Map.of("widgets",List.of("incident_overview","critical_alerts"),"categories",List.of("health"),"locations",List.of("Alangudi"),"alerts",Map.of("critical",true,"high",true,"weather",false,"overdueActions",true,"email",false));
        assertEquals(200,request("PUT","/users/me/dashboard-preferences",prefs,token).statusCode());assertEquals(2,json(request("GET","/users/me/dashboard-preferences",null,token)).path("widgets").size());
        var action=Map.of("title","Check bridge safety","description","Inspect source evidence before field work","departmentId","pwd","priority","high","dueDate",LocalDate.now().plusDays(2).toString());
        var created=request("POST","/actions",action,token);assertEquals(200,created.statusCode(),created.body());String id=json(created).path("id").asText();
        var commented=request("POST","/actions/"+id+"/comments",Map.of("text","Inspection scheduled","author","Impersonated person"),token);assertEquals(200,commented.statusCode(),commented.body());assertEquals("Platform Administrator",json(commented).path("comments").path(0).path("author").asText());
        assertEquals("completed",json(request("PATCH","/actions/"+id,Map.of("status","completed"),token)).path("status").asText());
        String email="officer-"+UUID.randomUUID()+"@tests.invalid";var user=request("POST","/auth/users",Map.of("name","Health Officer","email",email,"password","Test-officer-password-2026","role","department_officer","departmentId","health"),token);assertEquals(200,user.statusCode(),user.body());String officer=login(email,"Test-officer-password-2026");
        assertEquals(403,request("GET","/sources",null,officer).statusCode());assertEquals(403,request("POST","/news/collect",null,officer).statusCode());assertEquals(403,request("PATCH","/actions/"+id,Map.of("status","open"),officer).statusCode());
        assertEquals(403,request("GET","/integrations/lms",null,officer).statusCode());
        assertEquals(403,request("GET","/districts/pudukkottai/news/status",null,officer).statusCode());
        assertTrue(json(request("GET","/districts/pudukkottai/actions",null,officer)).isArray());
        String officerId=json(user).path("id").asText();
        assertEquals(200,request("PATCH","/auth/users/"+officerId,Map.of("password","Replacement-test-password-2026"),token).statusCode());
        assertEquals(401,request("GET","/districts/pudukkottai/actions",null,officer).statusCode());
        assertNotNull(login(email,"Replacement-test-password-2026"));
        var report=request("POST","/districts/pudukkottai/reports",Map.of("type","daily","from",LocalDate.now().toString(),"to",LocalDate.now().toString(),"categories",List.of(),"departmentIds",List.of(),"priorities",List.of()),token);assertEquals(200,report.statusCode(),report.body());assertEquals("Pudukkottai",json(report).path("district").asText());
    }
    NewsArticle article(String title,String taluk,String category,String hash){var a=new NewsArticle();a.setTitle(title);a.setTaluk(taluk);a.setCategory(category);a.setSeverity("Medium");a.setPublishedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusHours(3));a.setCollectedAt(LocalDateTime.now());a.setProcessingStatus("PROCESSED");a.setUrl("https://example.org/"+hash);a.setUrlHash(hash);a.setSource("Test source");return articles.save(a);}
    @Test void groupingIsIncrementalAndPreservesReviewAndLocationBoundaries() throws Exception {
        String suffix=UUID.randomUUID().toString();var a=article("Bridge closure safety inspection "+suffix,"Alangudi","Infrastructure","one"+suffix);grouping.build();a=articles.findById(a.getId()).orElseThrow();Long id=a.getIncidentId();assertNotNull(id);
        var i=incidents.findById(id).orElseThrow();i.setReviewStatus("VERIFIED");i.setDepartmentId("pwd");incidents.save(i);
        var same=article(a.getTitle(),"Alangudi","Infrastructure","two"+suffix);var different=article(a.getTitle(),"Aranthangi","Infrastructure","three"+suffix);grouping.build();grouping.build();
        assertEquals(id,articles.findById(same.getId()).orElseThrow().getIncidentId());assertNotEquals(id,articles.findById(different.getId()).orElseThrow().getIncidentId());assertEquals("VERIFIED",incidents.findById(id).orElseThrow().getReviewStatus());assertEquals(2,incidents.findById(id).orElseThrow().getArticleCount());
        String token=admin();var dto=request("GET","/intelligence/incidents/LIVE-"+id,null,token);assertEquals(200,dto.statusCode(),dto.body());assertEquals("verified",json(dto).path("incident").path("verification").asText());assertFalse(json(dto).path("incident").path("location").path("resolved").asBoolean());
    }
    @Test void invalidActionAndSourceUrlsAreRejectedAndCredentialsRoundTrip() throws Exception {
        assertThrows(IllegalArgumentException.class,()->urls.check("http://127.0.0.1"));assertThrows(IllegalArgumentException.class,()->urls.check("file:///etc/passwd"));assertThrows(IllegalArgumentException.class,()->urls.check("https://user:password@example.org"));
        String encrypted=secrets.encrypt("test-only-value");assertNotEquals("test-only-value",encrypted);assertEquals("test-only-value",secrets.decrypt(encrypted));
        var r=request("POST","/actions",Map.of("title","","description","","departmentId","unknown","priority","urgent","dueDate","bad"),admin());assertEquals(400,r.statusCode());
    }
    @Test void datasetImportRequiresProvenanceAndReconciledAggregates() throws Exception {
        String token=admin();var data=Map.of("totals",Map.of("total",2,"pending",1,"approved",1,"rejected",0),"byDepartment",List.of(),"monthly",List.of(),"byType",List.of(),"recentRequests",List.of(Map.of("employeeName","Must never be stored")));
        var body=Map.of("sourceUrl","https://example.org/authorised-export","observedAt",Instant.now().minusSeconds(60).toString(),"authorised",true,"data",data);
        var r=request("POST","/districts/pudukkottai/datasets/leave/import",body,token);assertEquals(200,r.statusCode(),r.body());var response=json(request("GET","/districts/pudukkottai/leave/summary",null,token));assertEquals(0,response.path("recentRequests").size());assertEquals(2,response.path("totals").path("total").asInt());
    }
}
