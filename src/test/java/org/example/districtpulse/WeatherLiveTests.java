package org.example.districtpulse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.example.districtpulse.integration.WeatherLiveService;
import org.example.districtpulse.platform.DistrictGeography;
import tools.jackson.databind.json.JsonMapper;

class WeatherLiveTests {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final String sample="""
        {"utc_offset_seconds":19800,"current":{"time":"2026-11-16T17:15","temperature_2m":29.3,
        "relative_humidity_2m":65,"wind_speed_10m":2.8,"weather_code":3},
        "daily":{"time":["2026-11-16"],"temperature_2m_max":[30.6],"temperature_2m_min":[24.3],
        "precipitation_probability_max":[null],"precipitation_sum":[0.1],"weather_code":[51]}}
        """;
    @Test void forecastPreservesUnknownValuesAndDoesNotInventWarningsOrRecordedRainfall(){
        var result=WeatherLiveService.parse(mapper.readTree(sample));
        var day=(Map<?,?>)((List<?>)result.get("forecast")).get(0);
        assertNull(day.get("rainChance"));assertEquals(0.1,day.get("rainMm"));
        assertEquals("2026-11-16T11:45:00Z",((Map<?,?>)result.get("current")).get("observedAt"));
        assertEquals(List.of(),result.get("warnings"));assertEquals(List.of(),result.get("rainfall"));
        assertEquals(false,((Map<?,?>)result.get("provider")).get("warningsConnected"));
    }
    @Test void missingCurrentValuesDoNotBecomeZero(){
        assertThrows(IllegalArgumentException.class,()->WeatherLiveService.parse(mapper.readTree(sample.replace("\"temperature_2m\":29.3","\"temperature_2m\":null"))));
    }
    @Test void anotherDistrictNeverInheritsPudukkottaiCoordinates(){
        assertEquals(10.3861,new DistrictGeography("pudukkottai","","").latitude());
        var other=new DistrictGeography("another-district","","");assertTrue(other.metadata().isEmpty());
        assertThrows(IllegalArgumentException.class,other::latitude);
        assertThrows(IllegalArgumentException.class,()->new DistrictGeography("other","91","78"));
        assertEquals(12.5,new DistrictGeography("other","12.5","79").latitude());
    }
}
