package org.example.districtpulse.platform;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DistrictGeography {
    private final Double latitude,longitude;
    public DistrictGeography(@Value("${district.id}") String id,
            @Value("${district.latitude:}") String lat,@Value("${district.longitude:}") String lon) {
        if(lat.isBlank()&&lon.isBlank()&&"pudukkottai".equals(id)){latitude=10.3861;longitude=78.8042;}
        else if(lat.isBlank()&&lon.isBlank()){latitude=null;longitude=null;}
        else {latitude=Double.valueOf(lat);longitude=Double.valueOf(lon);
            if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new IllegalArgumentException("Invalid district coordinates");}
    }
    public double latitude(){if(latitude==null)throw new IllegalArgumentException("District coordinates are not configured");return latitude;}
    public double longitude(){if(longitude==null)throw new IllegalArgumentException("District coordinates are not configured");return longitude;}
    public Map<String,Object> metadata(){return latitude==null?Map.of():Map.of("latitude",latitude,"longitude",longitude,"zoom",10);}
}
