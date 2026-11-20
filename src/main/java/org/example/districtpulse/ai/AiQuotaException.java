package org.example.districtpulse.ai;
public class AiQuotaException extends IllegalStateException {
    private final String reason;
    public AiQuotaException(String reason){super(reason+": processing paused until the provider retry time");this.reason=reason;}
    public String reason(){return reason;}
}
