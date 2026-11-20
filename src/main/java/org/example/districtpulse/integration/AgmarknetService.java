package org.example.districtpulse.integration;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.math.BigDecimal;
import org.example.districtpulse.platform.DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AgmarknetService {
    public static final String RESOURCE="9ef84268-d588-465a-a308-a864a43d0070";
    private final DocumentStore store;private final String configuredKey,state,district;private final Path keyFile;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper mapper=JsonMapper.builder().build();private Instant retryAfter=Instant.EPOCH;private String retryStatus="retry_later";
    public AgmarknetService(DocumentStore store,@Value("${agmarknet.api-key:}") String key,
        @Value("${agmarknet.credentials-file:../.local/agmarknet.properties}") String file,
        @Value("${district.state}") String state,@Value("${district.name}") String district){this.store=store;this.configuredKey=key;this.keyFile=Path.of(file);this.state=state;this.district=district;}
    private String key(){if(!configuredKey.isBlank())return configuredKey.trim();Properties p=new Properties();try(var r=Files.newBufferedReader(keyFile)){p.load(r);return p.getProperty("agmarknet.api-key","").trim();}catch(Exception e){return "";}}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    public synchronized Map<String,Object> snapshot(String id){
        String docId="AGMARKNET-"+id;var saved=store.get(docId);Instant now=Instant.now();String key=key();
        if(key.isBlank())return saved==null?empty(id,"not_configured"):marked(saved,"stale_key_missing");
        if(saved!=null&&Instant.parse(saved.get("collectedAt").toString()).isAfter(now.minusSeconds(3600)))return freshness(saved);
        if(now.isBefore(retryAfter))return saved==null?empty(id,retryStatus):marked(saved,"stale_"+retryStatus);
        try{
            List<Map<String,Object>> rows=new ArrayList<>();Set<String> seen=new HashSet<>();boolean complete=false;int rejected=0;
            Instant deadline=Instant.now().plusSeconds(15);
            for(int offset=0;offset<10000;offset+=500){
                String url="https://api.data.gov.in/resource/"+RESOURCE+"?api-key="+enc(key)+"&format=json&filters%5Bstate%5D="+enc(state)+"&filters%5Bdistrict%5D="+enc(district)+"&limit=500&offset="+offset;
                Duration remaining=Duration.between(Instant.now(),deadline);if(remaining.isNegative()||remaining.isZero())throw new IllegalArgumentException();
                var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(remaining).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                byte[] body;try(var stream=response.body()){body=stream.readNBytes(2_000_001);}
                if(response.statusCode()==429){retryStatus="rate_limited";retryAfter=now.plusSeconds(3600);return saved==null?empty(id,retryStatus):marked(saved,"stale_rate_limited");}
                if(response.statusCode()!=200||body.length>2_000_000)throw new IllegalArgumentException();
                var root=mapper.readTree(body);var records=root.path("records");if(!records.isArray()||(!root.path("status").isMissingNode()&&!"ok".equalsIgnoreCase(root.path("status").asText())))throw new IllegalArgumentException();
                for(var record:records){var parsed=parseRecord(record,state,district);if(parsed==null){rejected++;continue;}
                    String identity=parsed.get("market")+"|"+parsed.get("commodity")+"|"+parsed.get("variety")+"|"+parsed.get("grade")+"|"+parsed.get("date");
                    if(seen.add(identity))rows.add(parsed);
                }
                if(records.size()<500||(root.path("total").isIntegralNumber()&&offset+records.size()>=root.path("total").asLong())){complete=true;break;}
            }
            if(!complete)throw new IllegalArgumentException();
            rows.sort(Comparator.comparing((Map<String,Object> r)->r.get("date").toString()).reversed());
            Map<String,Object> doc=new LinkedHashMap<>();doc.put("district",id);doc.put("sourceUrl","https://www.data.gov.in/resource/current-daily-price-various-commodities-various-markets-mandi");
            doc.put("collectedAt",now.toString());doc.put("observedAt",rows.isEmpty()?now.toString():LocalDate.parse(rows.get(0).get("date").toString()).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toString());
            doc.put("method","agmarknet_ogd");doc.put("status",rows.isEmpty()?"no_records":"connected");doc.put("rejectedRecords",rejected);
            doc.put("data",data(rows,rows.isEmpty()?"no_records":"connected"));store.save(docId,"agriculture_live",id,doc);retryAfter=Instant.EPOCH;return freshness(doc);
        }catch(InterruptedException e){Thread.currentThread().interrupt();retryStatus="retry_later";retryAfter=now.plusSeconds(60);}
        catch(Exception e){retryStatus="retry_later";retryAfter=now.plusSeconds(60);}
        return saved==null?empty(id,"fetch_failed"):marked(saved,"stale");
    }
    private Map<String,Object> freshness(Map<String,Object> doc){if(!"no_records".equals(doc.get("status"))&&Instant.parse(doc.get("observedAt").toString()).isBefore(Instant.now().minusSeconds(172800)))return marked(doc,"stale");return doc;}
    private Map<String,Object> marked(Map<String,Object> saved,String status){var doc=new LinkedHashMap<>(saved);doc.put("status",status);return doc;}
    private Map<String,Object> empty(String id,String status){return Map.of("district",id,"status",status,"method","agmarknet_ogd","data",data(List.of(),status));}
    private Map<String,Object> data(List<Map<String,Object>> rows,String status){
        Map<String,Object> out=new LinkedHashMap<>();out.put("latestPrices",rows);out.put("commodities",rows.stream().map(r->r.get("commodity")).distinct().toList());
        for(String field:List.of("prices","arrivals","schemes","visits","villages"))out.put(field,List.of());
        out.put("provider",Map.of("name","AGMARKNET via data.gov.in","resourceId",RESOURCE,"status",status,"note","Daily price records only. Arrival quantities, historical change and AGRISNET scheme coverage are not supplied by this adapter. Price unit is not confirmed from the response fields."));return out;
    }
    public static Map<String,Object> parseRecord(JsonNode r,String state,String district){
        if(!state.trim().equalsIgnoreCase(r.path("state").asText().trim())||!district.trim().equalsIgnoreCase(r.path("district").asText().trim()))return null;
        try{
            String market=r.path("market").asText().trim(),commodity=r.path("commodity").asText().trim();if(market.isEmpty()||commodity.isEmpty())return null;
            LocalDate date=LocalDate.parse(r.path("arrival_date").asText(),DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
            if(date.isAfter(LocalDate.now(ZoneId.of("Asia/Kolkata"))))return null;
            BigDecimal min=new BigDecimal(r.path("min_price").asText()),max=new BigDecimal(r.path("max_price").asText()),modal=new BigDecimal(r.path("modal_price").asText());
            if(min.signum()<0||max.compareTo(min)<0||modal.compareTo(min)<0||modal.compareTo(max)>0)return null;
            Map<String,Object> row=new LinkedHashMap<>();row.put("market",market);row.put("commodity",commodity);row.put("variety",r.path("variety").asText());row.put("grade",r.path("grade").asText());
            row.put("date",date.toString());row.put("minPrice",min);row.put("maxPrice",max);row.put("modalPrice",modal);row.put("unit","Source unit unverified");row.put("changePct",null);return row;
        }catch(Exception e){return null;}
    }
}
