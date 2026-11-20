package org.example.districtpulse.ai;

import java.time.*;
import java.util.*;
import org.example.districtpulse.platform.DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AiProviderState {
    private final DocumentStore store;private final String provider,model;
    public AiProviderState(DocumentStore store,@Value("${ai.provider:mock}") String provider,@Value("${gemini.model:gemini-3.8-flash}") String model){this.store=store;this.provider=provider;this.model=model;}
    private String key(){return "AI-HEALTH-"+provider+"-"+model;}
    public Map<String,Object> current(){
        var state=store.get(key());
        if(state!=null&&state.get("retryAt") instanceof String retry&&Instant.parse(retry).isAfter(Instant.now()))return state;
        return Map.of("provider",provider,"model",model,"status","mock".equals(provider)?"demo":"ready");
    }
    public void requireAvailable(){var state=current();if("blocked".equals(state.get("status")))throw new AiQuotaException(state.get("reasonCode").toString());}
    public void succeeded(){store.save(key(),"ai_health","system",Map.of("provider",provider,"model",model,"status","ready","lastSuccessAt",Instant.now().toString()));}
    public AiQuotaException blocked(String responseBody){var state=quotaState(responseBody,model,Instant.now());store.save(key(),"ai_health","system",state);return new AiQuotaException(state.get("reasonCode").toString());}
    public static Map<String,Object> quotaState(String body,String model,Instant now){
        boolean daily=false;long seconds=60;String limit=null;
        try{
            var error=JsonMapper.builder().build().readTree(body);
            for(var detail:error.path("error").path("details")){
                if(detail.path("@type").asText().endsWith("QuotaFailure"))for(var violation:detail.path("violations")){
                    if(violation.path("quotaId").asText().toLowerCase(Locale.ROOT).contains("perday"))daily=true;
                    String value=violation.path("quotaValue").asText();if(value.matches("[0-9]{1,9}"))limit=value;
                }
                if(detail.path("@type").asText().endsWith("RetryInfo")){
                    String delay=detail.path("retryDelay").asText();if(delay.matches("[0-9]+(?:\\.[0-9]+)?s"))seconds=(long)Math.ceil(Double.parseDouble(delay.substring(0,delay.length()-1)));
                }
            }
        }catch(Exception ignored){/* Never persist raw provider errors or account/key details. */}
        if(daily&&seconds==60)seconds=Duration.between(now,now.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant()).getSeconds();
        seconds=Math.max(1,Math.min(seconds,172800));Map<String,Object> state=new LinkedHashMap<>();state.put("provider","gemini");state.put("model",model);state.put("status","blocked");state.put("reasonCode",daily?"DAILY_QUOTA_EXHAUSTED":"RATE_LIMITED");state.put("retryAt",now.plusSeconds(seconds).toString());state.put("checkedAt",now.toString());if(limit!=null)state.put("requestLimit",Long.parseLong(limit));return state;
    }
}
