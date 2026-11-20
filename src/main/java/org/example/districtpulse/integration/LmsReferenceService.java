package org.example.districtpulse.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.example.districtpulse.platform.DocumentStore;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
public class LmsReferenceService {
    public static final String URL="https://staging.d1ilryfopwulnt.amplifyapp.com/";
    private final DocumentStore store;private final LmsSnapshotParser parser=new LmsSnapshotParser();
    public LmsReferenceService(DocumentStore store){this.store=store;}
    public Map<String,Object> get(){var saved=store.get("LMS-REFERENCE");return saved==null?Map.of("status","not_synced","sourceUrl",URL,"scope","external_reference_only"):saved;}
    public synchronized Map<String,Object> sync(String actor){
        try{
            var response=Jsoup.connect(URL).followRedirects(false).timeout(20000).maxBodySize(2_000_000).execute();
            if(response.statusCode()!=200)throw new IllegalArgumentException("LMS source did not return HTTP 200");
            String html=response.body();if(html.length()>1_500_000)throw new IllegalArgumentException("LMS source is too large");
            Map<String,Object> result=new LinkedHashMap<>(parser.parse(html));result.put("sourceUrl",URL);result.put("status","reference_demo_snapshot");result.put("fetchedAt",Instant.now().toString());result.put("sourceSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(html.getBytes(StandardCharsets.UTF_8))));
            store.save("LMS-REFERENCE","external_reference","yadadri-bhuvanagiri",result);store.audit(actor,"lms.reference_synced","LMS-REFERENCE");return result;
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("LMS reference sync failed; the last successful snapshot is retained");}
    }
}
