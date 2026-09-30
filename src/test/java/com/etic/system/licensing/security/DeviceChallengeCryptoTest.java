package com.etic.system.licensing.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceChallengeCryptoTest {
	@Test void simulatedAndroidKeyPairSignsChallengeAndBackendVerifies() throws Exception {KeyPair keys=keyPair();byte[] nonce="secure-test-nonce".getBytes(StandardCharsets.UTF_8);String encodedNonce=Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(keys.getPrivate());signer.update(nonce);String signature=Base64.getEncoder().encodeToString(signer.sign());String publicKey=Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());assertTrue(DeviceSecurityService.verifySignature(publicKey,encodedNonce,signature));}
	@Test void invalidSignatureIsRejected() throws Exception {KeyPair trusted=keyPair();KeyPair attacker=keyPair();byte[] nonce="secure-test-nonce".getBytes(StandardCharsets.UTF_8);Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(attacker.getPrivate());signer.update(nonce);assertFalse(DeviceSecurityService.verifySignature(Base64.getEncoder().encodeToString(trusted.getPublic().getEncoded()),Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),Base64.getEncoder().encodeToString(signer.sign())));}
	private KeyPair keyPair() throws Exception {KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));return generator.generateKeyPair();}
}
