package org.example.districtpulse;
import java.time.*;
import java.util.*;
import org.example.districtpulse.ai.*;
import org.example.districtpulse.news.*;
import org.example.districtpulse.platform.DocumentStore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiQuotaTests {
    @Test void dailyQuotaPreservesRetryTimeAndLimitWithoutRawProviderDetails(){
        var now=Instant.parse("2026-11-16T09:00:00Z");
        String error="""
            {"error":{"message":"do not retain TEST-SECRET or project details","details":[
              {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"20"}]},
              {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"3600s"}]}}
            """;
        var state=AiProviderState.quotaState(error,"test-model",now);
        assertEquals("DAILY_QUOTA_EXHAUSTED",state.get("reasonCode"));assertEquals(20L,state.get("requestLimit"));assertEquals(now.plusSeconds(3600).toString(),state.get("retryAt"));assertFalse(state.toString().contains("TEST-SECRET"));
    }
    @Test void persistedBlockIsScopedToConfiguredModelAndPreventsCalls(){
        var store=mock(DocumentStore.class);var block=AiProviderState.quotaState("{}","blocked-model",Instant.now());when(store.get("AI-HEALTH-gemini-blocked-model")).thenReturn(block);
        assertThrows(AiQuotaException.class,()->new AiProviderState(store,"gemini","blocked-model").requireAvailable());
        assertDoesNotThrow(()->new AiProviderState(store,"gemini","another-model").requireAvailable());
    }
    @Test void quotaFailureStopsTheBatchAndLeavesRemainingArticlesPending(){
        var repo=mock(NewsArticleRepository.class);var llm=mock(LlmClient.class);var first=new NewsArticle();first.setTitle("Pudukkottai test news");var second=new NewsArticle();second.setTitle("Another test report");
        when(repo.findByProcessingStatus("NEW")).thenReturn(List.of(first,second));when(llm.extract(anyString())).thenThrow(new AiQuotaException("DAILY_QUOTA_EXHAUSTED"));
        assertEquals(0,new NewsProcessingService(repo,llm).processPending());verify(llm,times(1)).extract(anyString());verify(repo).save(first);assertEquals("FAILED",first.getProcessingStatus());assertEquals("NEW",second.getProcessingStatus());
    }
}
