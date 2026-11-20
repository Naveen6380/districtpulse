package org.example.districtpulse.integration;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.example.districtpulse.platform.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WeatherLiveService {
    private final DocumentStore store; private final DistrictGeography geo; private final boolean enabled;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final Map<String,Instant> retryAfter=new HashMap<>();
    public WeatherLiveService(DocumentStore store,DistrictGeography geo,@Value("${weather.live-enabled:true}") boolean enabled){this.store=store;this.geo=geo;this.enabled=enabled;}
    public synchronized Map<String,Object> snapshot(String district){
        if(!enabled)return null;
        String key="WEATHER-LIVE-"+district;var saved=store.get(key);Instant now=Instant.now();
        if(saved!=null&&Instant.parse(saved.get("collectedAt").toString()).isAfter(now.minusSeconds(900)))return saved;
        if(now.isBefore(retryAfter.getOrDefault(district,Instant.EPOCH)))return staleOrFail(saved);
        try{
            String url="https://api.open-meteo.com/v1/forecast?latitude="+geo.latitude()+"&longitude="+geo.longitude()
                +"&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                +"&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum"
                +"&timezone=Asia%2FKolkata&forecast_days=7";
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;try(var stream=response.body()){body=stream.readNBytes(1_000_001);}
            if(response.statusCode()!=200||body.length>1_000_000)throw new IllegalArgumentException("Weather provider unavailable");
            var data=parse(mapper.readTree(body));
            Map<String,Object> result=new LinkedHashMap<>();result.put("district",district);result.put("status","live_model_forecast");
            result.put("sourceUrl","https://open-meteo.com/en/docs");result.put("requestUrl",url);result.put("collectedAt",now.toString());
            result.put("observedAt",((Map<?,?>)data.get("current")).get("observedAt"));result.put("method","open_meteo_forecast");
            result.put("data",data);store.save(key,"weather_live",district,result);retryAfter.remove(district);return result;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return staleOrFail(saved);}
        catch(Exception e){retryAfter.put(district,now.plusSeconds(60));return staleOrFail(saved);}
    }
    private Map<String,Object> staleOrFail(Map<String,Object> saved){
        if(saved==null)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Weather provider unavailable; retry shortly");
        var result=new LinkedHashMap<>(saved);result.put("status","stale");return result;
    }
    public static Map<String,Object> parse(JsonNode root){
        var c=root.path("current");for(String field:List.of("temperature_2m","relative_humidity_2m","wind_speed_10m","weather_code"))
            if(!c.path(field).isNumber())throw new IllegalArgumentException("Missing current weather value");
        int offset=root.path("utc_offset_seconds").asInt();
        String observed=LocalDateTime.parse(c.path("time").asText()).toInstant(ZoneOffset.ofTotalSeconds(offset)).toString();
        Map<String,Object> current=Map.of("tempC",c.path("temperature_2m").asDouble(),"humidity",c.path("relative_humidity_2m").asDouble(),
            "windKmph",c.path("wind_speed_10m").asDouble(),"condition",condition(c.path("weather_code").asInt()),"observedAt",observed);
        var daily=root.path("daily");List<Map<String,Object>> forecast=new ArrayList<>();
        for(int i=0;i<daily.path("time").size();i++){
            Map<String,Object> day=new LinkedHashMap<>();day.put("date",daily.path("time").get(i).asText());
            for(var field:Map.of("maxC","temperature_2m_max","minC","temperature_2m_min","rainChance","precipitation_probability_max","rainMm","precipitation_sum").entrySet()){
                var value=daily.path(field.getValue()).path(i);day.put(field.getKey(),value.isNumber()?value.asDouble():null);
            }
            var code=daily.path("weather_code").path(i);day.put("condition",code.isNumber()?condition(code.asInt()):"Unavailable");forecast.add(day);
        }
        if(forecast.isEmpty())throw new IllegalArgumentException("Missing forecast");
        return Map.of("current",current,"forecast",forecast,"warnings",List.of(),"rainfall",List.of(),"monthly",List.of(),
            "provider",Map.of("name","Open-Meteo","url","https://open-meteo.com/","kind","model_forecast","warningsConnected",false,
                "note","Model estimates for the district reference point; not district-wide measurements. Official IMD warnings are not connected."));
    }
    private static String condition(int code){return switch(code){case 0->"Clear sky";case 1,2->"Partly cloudy";case 3->"Overcast";case 45,48->"Fog";
        case 51,53,55,56,57->"Drizzle";case 61,63,65,66,67->"Rain";case 71,73,75,77->"Snow";case 80,81,82->"Rain showers";
        case 85,86->"Snow showers";case 95,96,99->"Thunderstorm";default->"Unavailable";};}
}
