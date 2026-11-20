package org.example.districtpulse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.example.districtpulse.integration.AgmarknetService;
import tools.jackson.databind.json.JsonMapper;
class AgmarknetTests {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final String sample="""
      {"state":"Tamil Nadu","district":"Pudukkottai","market":"Test market","commodity":"Paddy",
       "variety":"Test variety","grade":"FAQ","arrival_date":"17/11/2026","min_price":"1000","max_price":"1200","modal_price":"1100"}
      """;
    @Test void preservesDateVarietyPricesAndUnknownChange(){var row=AgmarknetService.parseRecord(mapper.readTree(sample),"Tamil Nadu","Pudukkottai");assertNotNull(row);assertEquals("2026-11-17",row.get("date"));assertEquals("Test variety",row.get("variety"));assertNull(row.get("changePct"));assertFalse(row.containsKey("arrivals"));assertEquals("Source unit unverified",row.get("unit"));}
    @Test void rejectsOtherJurisdictions(){assertNull(AgmarknetService.parseRecord(mapper.readTree(sample),"Tamil Nadu","Chennai"));assertNull(AgmarknetService.parseRecord(mapper.readTree(sample),"Kerala","Pudukkottai"));}
    @Test void rejectsImpossibleDateAndPriceRange(){for(String changed:new String[]{sample.replace("17/11/2026","31/02/2026"),sample.replace("1100","1300"),sample.replace("1000","-1"),sample.replace("1100","NaN")})assertNull(AgmarknetService.parseRecord(mapper.readTree(changed),"Tamil Nadu","Pudukkottai"));}
}
