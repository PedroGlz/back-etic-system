package com.etic.system.licensing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.licensing")
public class LicensingSecurityProperties {
	private long enrollmentValidityHours = 72;
	private long challengeValidityMinutes = 5;
	private long offlineValidityDays = 7;
	private String signingPrivateKey = "";
	private String signingKeyId = "local-development";
	public long getEnrollmentValidityHours(){return enrollmentValidityHours;}
	public void setEnrollmentValidityHours(long value){enrollmentValidityHours=value;}
	public long getChallengeValidityMinutes(){return challengeValidityMinutes;}
	public void setChallengeValidityMinutes(long value){challengeValidityMinutes=value;}
	public long getOfflineValidityDays(){return offlineValidityDays;}
	public void setOfflineValidityDays(long value){offlineValidityDays=value;}
	public String getSigningPrivateKey(){return signingPrivateKey;}
	public void setSigningPrivateKey(String value){signingPrivateKey=value;}
	public String getSigningKeyId(){return signingKeyId;}
	public void setSigningKeyId(String value){signingKeyId=value;}
}
