package org.example.districtpulse.news;

import org.example.districtpulse.incident.IncidentService;
import org.example.districtpulse.platform.DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

@Service @EnableScheduling
public class PipelineCoordinator {
    private final NewsCollectionService collection;private final NewsProcessingService processing;private final IncidentService incidents;private final DocumentStore store;
    private final NewsArticleRepository articles;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private volatile String active;
    @Value("${app.pipeline-enabled:false}") private boolean scheduled;
    public PipelineCoordinator(NewsCollectionService collection,NewsProcessingService processing,IncidentService incidents,DocumentStore store,NewsArticleRepository articles){this.collection=collection;this.processing=processing;this.incidents=incidents;this.store=store;this.articles=articles;}
    public synchronized Map<String,Object> start(boolean collect){
        if(active!=null)return Map.of("id",active,"status","running");String id="JOB-"+UUID.randomUUID();active=id;
        store.save(id,"pipeline","system",Map.of("id",id,"status","queued","startedAt",Instant.now().toString()));
        worker.submit(()->{Map<String,Object> result=new LinkedHashMap<>();result.put("id",id);result.put("startedAt",Instant.now().toString());result.put("status","running");store.save(id,"pipeline","system",result);try{
            if(collect)result.put("collected",collection.collect());result.put("processed",processing.processPending());result.put("incidents",incidents.build());int failed=articles.findByProcessingStatus("FAILED").size();result.put("failedArticles",failed);result.put("status",failed>0?"partial":"completed");if(failed>0)result.put("message",failed+" articles failed extraction. Inspect provider quota/configuration before retrying.");
        }catch(Exception e){result.put("status","failed");result.put("message","Pipeline failed; review source/provider configuration");}finally{result.put("finishedAt",Instant.now().toString());store.save(id,"pipeline","system",result);active=null;}});
        return Map.of("id",id,"status","queued");
    }
    @Scheduled(fixedDelayString="${app.pipeline-delay-ms:1800000}",initialDelay=60000) public void scheduled(){if(scheduled)start(true);}
    @PreDestroy public void stop(){worker.shutdownNow();}
}
