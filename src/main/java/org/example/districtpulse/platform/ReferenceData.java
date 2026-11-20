package org.example.districtpulse.platform;
import java.util.*;
public final class ReferenceData {
    public static final Map<String,Map<String,String>> DEPARTMENTS=new LinkedHashMap<>();
    static {
        add("revenue","Revenue & Disaster Management","Revenue","District Revenue Officer");
        add("pwd","Public Works Department","PWD","Executive Engineer (PWD)");
        add("wrd","Water Resources Department","WRD","Executive Engineer (WRD)");
        add("agri","Agriculture & Farmers Welfare","Agriculture","Joint Director of Agriculture");
        add("health","Health & Family Welfare","Health","Deputy Director of Health Services");
        add("edu","School Education","Education","Chief Educational Officer");
        add("rural","Rural Development & Panchayat Raj","Rural Dev.","Project Director, DRDA");
        add("municipal","Municipal Administration","Municipal","Municipal Commissioner");
        add("power","Electricity Distribution","Electricity","Superintending Engineer");
    }
    private static void add(String id,String name,String shortName,String head){DEPARTMENTS.put(id,Map.of("id",id,"name",name,"shortName",shortName,"headDesignation",head));}
    public static String category(String category){return switch(category==null?"":category){
        case "Weather"->"weather"; case "Flood"->"flood"; case "Agriculture"->"agriculture";case "Health"->"health";
        case "Education"->"education";case "Infrastructure","Roads"->"infrastructure";case "Public safety"->"public_safety";
        case "Public grievances"->"grievance";case "Government administration"->"governance";default->"other";};}
    public static String department(String category){return switch(category){case "flood"->"wrd";case "agriculture"->"agri";case "health"->"health";case "education"->"edu";case "infrastructure"->"pwd";default->"revenue";};}
}
