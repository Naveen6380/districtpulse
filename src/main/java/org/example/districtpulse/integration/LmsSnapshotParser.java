package org.example.districtpulse.integration;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/** Reads a narrowly defined literal subset; never evaluates JavaScript from a remote page. */
public class LmsSnapshotParser {
    public Map<String,Object> parse(String html) {
        String scripts=Jsoup.parse(html).select("script:not([src])").stream().map(e->e.data()).reduce("",(a,b)->a+"\n"+b);
        if(!scripts.contains("demo-chip") || !html.contains("Yadadri Bhuvanagiri"))throw new IllegalArgumentException("LMS prototype signature changed; adapter review required");
        var date=Pattern.compile("const\\s+TODAY\\s*=\\s*new Date\\((\\d{4}),\\s*(\\d{1,2}),\\s*(\\d{1,2})\\)").matcher(scripts);
        if(!date.find())throw new IllegalArgumentException("LMS reference date not found");
        LocalDate referenceDate=LocalDate.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2))+1,Integer.parseInt(date.group(3)));
        List<Map<String,Object>> users=array(scripts,"USERS"),requests=array(scripts,"REQUESTS");
        Map<String,String> cadres=new HashMap<>();Map<String,Integer> profiles=new TreeMap<>();
        for(var u:users){String id=text(u,"id"),role=text(u,"role");if(!Set.of("admin","manager","employee").contains(role))throw new IllegalArgumentException("Unknown LMS role");if(cadres.put(id,text(u,"cadre"))!=null)throw new IllegalArgumentException("Duplicate LMS user reference");profiles.merge(role,1,Integer::sum);}
        if(!profiles.keySet().containsAll(Set.of("admin","manager","employee")))throw new IllegalArgumentException("LMS profiles are incomplete");
        Map<String,Integer> totals=counts();Map<String,Map<String,Integer>> byCadre=new TreeMap<>(),monthly=new TreeMap<>();Map<String,Integer> types=new TreeMap<>();Set<String> seen=new HashSet<>();
        for(var r:requests){if(!seen.add(text(r,"id")))throw new IllegalArgumentException("Duplicate LMS request");String cadre=cadres.get(text(r,"uid"));if(cadre==null)throw new IllegalArgumentException("Unknown LMS user reference");String status=text(r,"status").toLowerCase(Locale.ROOT);if(!totals.containsKey(status)||"total".equals(status))throw new IllegalArgumentException("Unknown LMS leave status");
            String applied=text(r,"applied");LocalDate application=LocalDate.parse(applied),from=LocalDate.parse(text(r,"from")),to=LocalDate.parse(text(r,"to"));if(to.isBefore(from)||application.isAfter(referenceDate))throw new IllegalArgumentException("Invalid LMS dates");
            String type=text(r,"type");if(!type.matches("[A-Z]{1,8}"))throw new IllegalArgumentException("Invalid leave type");
            increment(totals,status);increment(byCadre.computeIfAbsent(cadre,k->counts()),status);increment(monthly.computeIfAbsent(applied.substring(0,7),k->counts()),status);types.merge(type,1,Integer::sum);
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("sourceDistrict","Yadadri Bhuvanagiri");result.put("sourceState","Telangana");result.put("referenceDate",referenceDate.toString());result.put("isDemo",true);result.put("scope","external_reference_only");result.put("sourceKind","static_demo_prototype");result.put("accountCount",users.size());result.put("officerCount",users.size()-profiles.getOrDefault("admin",0));result.put("employeeCount",profiles.getOrDefault("employee",0));result.put("countUnit","leave_requests");result.put("profileCounts",profiles);result.put("totals",totals);
        result.put("byCadre",byCadre.entrySet().stream().map(e->row("cadre",e.getKey(),e.getValue())).toList());result.put("monthly",monthly.entrySet().stream().map(e->row("month",e.getKey(),e.getValue())).toList());result.put("byType",types.entrySet().stream().map(e->Map.of("type",e.getKey(),"count",e.getValue())).toList());
        result.put("limitations",List.of("Published static prototype data, not a live government API","Reference date is fixed by the source; fetched time does not establish data freshness","Client-side changes are session-local and are not fetched by this adapter","Not included in Pudukkottai district statistics","Employee identities, reasons, attachments and individual leave records are not retained"));return result;
    }
    private Map<String,Object> row(String field,String value,Map<String,Integer> counts){Map<String,Object> row=new LinkedHashMap<>();row.put(field,value);row.putAll(counts);return row;}
    private Map<String,Integer> counts(){Map<String,Integer> m=new LinkedHashMap<>();for(String k:List.of("total","pending","approved","rejected","cancelled"))m.put(k,0);return m;}
    private void increment(Map<String,Integer> counts,String status){counts.merge("total",1,Integer::sum);counts.merge(status,1,Integer::sum);}
    private String text(Map<String,Object> row,String key){if(!(row.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("Missing LMS field: "+key);return s;}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> array(String scripts,String name){var declaration=Pattern.compile("(?:let|const)\\s+"+name+"\\s*=\\s*\\[").matcher(scripts);if(!declaration.find())throw new IllegalArgumentException("LMS array missing: "+name);Object parsed=new LiteralReader(scripts,declaration.end()-1).value(0);if(!(parsed instanceof List<?> rows)||rows.isEmpty()||rows.size()>10000||rows.stream().anyMatch(r->!(r instanceof Map)))throw new IllegalArgumentException("Invalid LMS array: "+name);return (List<Map<String,Object>>)parsed;}

    static final class LiteralReader {
        private final String s;private int p;LiteralReader(String s,int p){this.s=s;this.p=p;}
        private void skip(){while(p<s.length()){if(Character.isWhitespace(s.charAt(p))){p++;continue;}if(s.startsWith("//",p)){int end=s.indexOf('\n',p);p=end<0?s.length():end;continue;}if(s.startsWith("/*",p)){int end=s.indexOf("*/",p+2);if(end<0)throw bad();p=end+2;continue;}break;}}
        private IllegalArgumentException bad(){return new IllegalArgumentException("Unsupported or malformed LMS literal; no remote code was executed");}
        private boolean consume(char c){skip();if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
        private void need(char c){if(!consume(c))throw bad();}
        Object value(int depth){if(depth>24)throw bad();skip();if(p>=s.length())throw bad();char c=s.charAt(p);
            if(c=='['){p++;List<Object> a=new ArrayList<>();if(consume(']'))return a;do{if(a.size()>10000)throw bad();a.add(value(depth+1));if(consume(']'))return a;need(',');}while(!consume(']'));return a;}
            if(c=='{'){p++;Map<String,Object> m=new LinkedHashMap<>();if(consume('}'))return m;do{skip();String key=s.charAt(p)=='\''||s.charAt(p)=='"'?string():identifier();need(':');if(m.containsKey(key))throw bad();m.put(key,value(depth+1));if(consume('}'))return m;need(',');}while(!consume('}'));return m;}
            if(c=='\''||c=='"')return string();
            if(c=='-'||Character.isDigit(c)){int start=p++;while(p<s.length()&&"0123456789.eE+-".indexOf(s.charAt(p))>=0)p++;try{return Double.valueOf(s.substring(start,p));}catch(Exception e){throw bad();}}
            String word=identifier();return switch(word){case "null"->null;case "true"->true;case "false"->false;default->throw bad();};
        }
        private String identifier(){skip();int start=p;while(p<s.length()&&(Character.isLetterOrDigit(s.charAt(p))||s.charAt(p)=='_'||s.charAt(p)=='$'))p++;if(start==p)throw bad();return s.substring(start,p);}
        private String string(){char quote=s.charAt(p++);StringBuilder b=new StringBuilder();while(p<s.length()){char c=s.charAt(p++);if(c==quote)return b.toString();if(c=='\\'){if(p>=s.length())throw bad();char escaped=s.charAt(p++);switch(escaped){case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case '\\','\'','"','/'->b.append(escaped);case 'u'->{if(p+4>s.length())throw bad();try{b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;}catch(Exception e){throw bad();}}default->throw bad();}}else b.append(c);if(b.length()>100000)throw bad();}throw bad();}
    }
}
