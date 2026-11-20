package org.example.districtpulse;
import org.example.districtpulse.integration.LmsSnapshotParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
class LmsSnapshotParserTests {
    private static final String PAGE="""
        <html><title>Yadadri Bhuvanagiri LMS</title><script>
        // demo-chip: fictional parser fixture
        const TODAY = new Date(2026,6,6);
        let USERS=[{id:'a',role:'admin',cadre:'Establishment',name:'TEST PERSON',email:'private@example.invalid'},
          {id:'m',role:'manager',cadre:'Revenue'}, {id:'e',role:'employee',cadre:'Revenue'}];
        let REQUESTS=[
          {id:'r1',uid:'e',type:'CL',from:'2026-07-08',to:'2026-07-09',applied:'2026-07-04',status:'Pending',reason:'PRIVATE REASON'},
          {id:'r2',uid:'m',type:'EL',from:'2026-06-01',to:'2026-06-03',applied:'2026-05-20',status:'Approved'},
          {id:'r3',uid:'e',type:'CL',from:'2026-04-01',to:'2026-04-01',applied:'2026-03-25',status:'Rejected'},
          {id:'r4',uid:'e',type:'OH',from:'2026-06-01',to:'2026-06-01',applied:'2026-05-01',status:'Cancelled'},
        ];
        </script></html>
        """;
    @Test void retainsOnlyAggregatesWithCancelledCountsAndOriginalJurisdiction(){
        var result=new LmsSnapshotParser().parse(PAGE);var totals=(Map<?,?>)result.get("totals");
        assertEquals(4,totals.get("total"));for(String status:new String[]{"pending","approved","rejected","cancelled"})assertEquals(1,totals.get(status));
        assertEquals("Yadadri Bhuvanagiri",result.get("sourceDistrict"));assertEquals("2026-07-06",result.get("referenceDate"));assertEquals(true,result.get("isDemo"));assertEquals("external_reference_only",result.get("scope"));
        String output=result.toString();assertFalse(output.contains("TEST PERSON"));assertFalse(output.contains("private@example.invalid"));assertFalse(output.contains("PRIVATE REASON"));assertFalse(output.contains("uid="));
    }
    @Test void rejectsExecutableJavascriptAndChangedSourceSchema(){
        assertThrows(IllegalArgumentException.class,()->new LmsSnapshotParser().parse(PAGE.replace("role:'employee'","role:dangerousFunction()")));
        assertThrows(IllegalArgumentException.class,()->new LmsSnapshotParser().parse(PAGE.replace("status:'Pending'","status:'Unrecognised'")));
        assertThrows(IllegalArgumentException.class,()->new LmsSnapshotParser().parse(PAGE.replace("const TODAY", "const DATE")));
    }
    @Test void rejectsBrokenReferencesAndDates(){
        assertThrows(IllegalArgumentException.class,()->new LmsSnapshotParser().parse(PAGE.replace("uid:'e'","uid:'unknown'")));
        assertThrows(IllegalArgumentException.class,()->new LmsSnapshotParser().parse(PAGE.replace("to:'2026-07-09'","to:'2026-07-01'")));
    }
}
