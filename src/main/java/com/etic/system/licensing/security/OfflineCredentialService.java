package com.etic.system.licensing.security;

import com.etic.system.licensing.config.LicensingSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfflineCredentialService {
	private final LicensingSecurityProperties properties;
	private final ObjectMapper mapper;
	public OfflineCredentialService(LicensingSecurityProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;}

	public String issue(Map<String,Object> claims) {
		try {
			Map<String,Object> header=new LinkedHashMap<>();header.put("alg","ES256");header.put("typ","ETIC-OFFLINE-LICENSE");header.put("keyId",properties.getSigningKeyId());
			String encodedHeader=encode(mapper.writeValueAsBytes(header));
			String encodedPayload=encode(mapper.writeValueAsBytes(claims));
			String signed=encodedHeader+"."+encodedPayload;
			Signature signature=Signature.getInstance("SHA256withECDSA");signature.initSign(privateKey());signature.update(signed.getBytes(StandardCharsets.US_ASCII));
			return signed+"."+encode(signature.sign());
		} catch (GeneralSecurityException | java.io.IOException ex) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No fue posible firmar la credencial offline",ex);
		}
	}

	public boolean verify(String credential,PublicKey publicKey) {
		try {String[] parts=credential.split("\\.");if(parts.length!=3)return false;Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(publicKey);verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));}catch(GeneralSecurityException|IllegalArgumentException ex){return false;}
	}

	private PrivateKey privateKey() throws GeneralSecurityException {
		String configured=properties.getSigningPrivateKey();
		if(configured==null||configured.isBlank())throw new InvalidKeyException("LICENSING_SIGNING_PRIVATE_KEY no está configurada");
		String value=configured.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
		return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(value)));
	}
	private String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
}
