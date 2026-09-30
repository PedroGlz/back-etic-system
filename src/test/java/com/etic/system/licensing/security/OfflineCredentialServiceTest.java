package com.etic.system.licensing.security;

import com.etic.system.licensing.config.LicensingSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class OfflineCredentialServiceTest {
	private KeyPair keys;
	private OfflineCredentialService service;
	@BeforeEach void setUp() throws Exception {KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));keys=generator.generateKeyPair();LicensingSecurityProperties properties=new LicensingSecurityProperties();properties.setSigningPrivateKey(Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()));properties.setSigningKeyId("test-key-2026");service=new OfflineCredentialService(properties,new ObjectMapper());}
	@Test void signsAndVerifiesOfflineCredential(){String value=service.issue(Map.of("licenseId","L1","deviceId","D1","offlineValidUntil","2026-10-23"));assertTrue(service.verify(value,keys.getPublic()));String header=new String(Base64.getUrlDecoder().decode(value.split("\\.")[0]));assertTrue(header.contains("test-key-2026"));}
	@Test void rejectsAlteredSignature(){String value=service.issue(Map.of("licenseId","L1"));String altered=value.substring(0,value.length()-2)+"AA";assertFalse(service.verify(altered,keys.getPublic()));}
	@Test void rejectsAlteredPayload(){String value=service.issue(Map.of("licenseId","L1"));String[] parts=value.split("\\.");String altered=parts[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString("ATTACK".getBytes())+"."+parts[2];assertFalse(service.verify(altered,keys.getPublic()));}
}
