package com.etic.system.licensing.security;

import com.etic.system.licensing.application.LicensingService;
import com.etic.system.licensing.config.LicensingSecurityProperties;
import com.etic.system.licensing.domain.*;
import com.etic.system.licensing.infrastructure.out.MySqlLicensingPersistenceAdapter;
import com.etic.system.licensing.security.DeviceSecurityService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RUN_ETIC_LOCAL_INTEGRATION",matches="true")
class OnlineLicensingFlowIntegrationTest {
	private NamedParameterJdbcTemplate licensing;
	private NamedParameterJdbcTemplate etic;
	private LicensingService licensingService;
	private DeviceSecurityService devices;
	private OfflineCredentialService credentials;
	private final List<String> deviceIds=new ArrayList<>();
	private final List<String> applicationIds=new ArrayList<>();

	@BeforeEach void setUp() throws Exception {
		licensing=jdbc(env("LICENSE_DATASOURCE_URL","jdbc:mysql://localhost:3307/license_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City"),env("LICENSE_DATASOURCE_USERNAME","etic_local"),env("LICENSE_DATASOURCE_PASSWORD","etic_local_password"));
		etic=jdbc(env("SPRING_DATASOURCE_URL","jdbc:mysql://localhost:3307/etic_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City"),env("SPRING_DATASOURCE_USERNAME","etic_local"),env("SPRING_DATASOURCE_PASSWORD","etic_local_password"));
		MySqlLicensingPersistenceAdapter persistence=new MySqlLicensingPersistenceAdapter(licensing,etic);licensingService=new LicensingService(persistence);
		KeyPair server=keyPair();LicensingSecurityProperties properties=new LicensingSecurityProperties();properties.setEnrollmentValidityHours(72);properties.setChallengeValidityMinutes(5);properties.setOfflineValidityDays(30);properties.setSigningKeyId("integration-test-key");properties.setSigningPrivateKey(Base64.getEncoder().encodeToString(server.getPrivate().getEncoded()));credentials=new OfflineCredentialService(properties,new ObjectMapper().findAndRegisterModules());devices=new DeviceSecurityService(licensing,etic,persistence,properties,credentials);
	}

	@AfterEach void cleanUp(){for(String id:deviceIds){licensing.update("DELETE FROM license_validation_events WHERE Id_Device=:id",Map.of("id",id));licensing.update("DELETE FROM device_challenges WHERE Id_Device=:id",Map.of("id",id));licensing.update("DELETE FROM device_enrollment_codes WHERE Id_Device=:id",Map.of("id",id));licensing.update("DELETE FROM licenses WHERE Id_Device=:id",Map.of("id",id));licensing.update("DELETE FROM licensed_devices WHERE Id_Device=:id",Map.of("id",id));}for(String id:applicationIds){licensing.update("DELETE FROM licensed_applications WHERE Id_Application=:id",Map.of("id",id));}}

	@Test void manualAndAutoFlowsReachSignedOfflineCredential() throws Exception {
		String userId=etic.query("SELECT Id_Usuario FROM usuarios WHERE Estatus='Activo' LIMIT 1",Map.of(),(r,n)->r.getString(1)).stream().findFirst().orElseThrow();
		String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);LicensedApplication app=licensingService.createApplication("Integración "+suffix,"com.etic.integration."+suffix,LicensingMode.USER_DEVICE,"ACTIVE",userId);applicationIds.add(app.id());

		ManualDeviceCreated manual=devices.createManual("Tablet integración", "ETIC", "TEST", "14", "PENDING", "Test temporal", userId);deviceIds.add(manual.device().id());assertTrue(manual.enrollmentCode().matches("ETIC-[A-Z0-9]{4}-[A-Z0-9]{4}"));
		KeyPair androidManual=keyPair();LicensedDevice enrolled=devices.enroll(manual.enrollmentCode(),identity(app.packageName(),androidManual),"127.0.0.1");assertEquals("ACTIVE",enrolled.status());assertThrows(RuntimeException.class,()->devices.enroll(manual.enrollmentCode(),identity(app.packageName(),androidManual),"127.0.0.1"));
		License manualLicense=licensingService.saveLicense(null,app.id(),enrolled.id(),userId,LocalDate.now(),LocalDate.now().plusDays(60),LicenseStatus.ACTIVE,userId);OfflineCredential manualCredential=completeChallenge(manualLicense,enrolled,androidManual);assertTrue(credentials.verify(manualCredential.credential(),serverPublicKey()));assertEquals(LocalDate.now().plusDays(30),manualCredential.offlineValidUntil());

		LicensedApplication deviceOnlyApp=licensingService.createApplication("Integración device "+suffix,"com.etic.device."+suffix,LicensingMode.DEVICE_ONLY,"ACTIVE",userId);applicationIds.add(deviceOnlyApp.id());KeyPair androidAuto=keyPair();LicensedDevice automatic=devices.registerAuto(identity(deviceOnlyApp.packageName(),androidAuto),"127.0.0.1");deviceIds.add(automatic.id());assertEquals("PENDING",automatic.status());assertThrows(RuntimeException.class,()->devices.registerAuto(identity(deviceOnlyApp.packageName(),androidAuto),"127.0.0.1"));automatic=devices.changeStatus(automatic.id(),"ACTIVE",userId);License autoLicense=licensingService.saveLicense(null,deviceOnlyApp.id(),automatic.id(),null,LocalDate.now(),LocalDate.now().plusDays(10),LicenseStatus.ACTIVE,userId);OfflineCredential autoCredential=completeChallenge(autoLicense,automatic,androidAuto);assertTrue(credentials.verify(autoCredential.credential(),serverPublicKey()));assertEquals(autoLicense.validUntil(),autoCredential.offlineValidUntil());String payload=new String(Base64.getUrlDecoder().decode(autoCredential.credential().split("\\.")[1]));assertFalse(payload.contains("userId"));

		ManualDeviceCreated expired=devices.createManual("Tablet código expirado",null,null,null,"PENDING",null,userId);deviceIds.add(expired.device().id());licensing.update("UPDATE device_enrollment_codes SET Expires_At=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE Id_Device=:id",Map.of("id",expired.device().id()));assertThrows(RuntimeException.class,()->devices.enroll(expired.enrollmentCode(),identity(app.packageName(),keyPairUnchecked()),"127.0.0.1"));assertThrows(RuntimeException.class,()->devices.enroll("ETIC-AAAA-BBBB",identity(app.packageName(),keyPairUnchecked()),"127.0.0.1"));
		assertEquals(3,licensing.queryForObject("SELECT COUNT(*) FROM licensed_devices WHERE Id_Device IN (:ids)",Map.of("ids",deviceIds),Integer.class));assertTrue(licensing.queryForObject("SELECT COUNT(*) FROM license_validation_events WHERE Id_Device IN (:ids)",Map.of("ids",deviceIds),Integer.class)>0);
	}

	private PublicKey integrationServerPublicKey;
	private PublicKey serverPublicKey(){return integrationServerPublicKey;}
	private OfflineCredential completeChallenge(License license,LicensedDevice device,KeyPair android)throws Exception{Challenge challenge=devices.createChallenge(device.id(),"127.0.0.1");String signature=sign(challenge,android);assertTrue(devices.verifyChallenge(device.id(),challenge.challengeId(),signature,"127.0.0.1").verified());assertThrows(RuntimeException.class,()->devices.verifyChallenge(device.id(),challenge.challengeId(),signature,"127.0.0.1"));OfflineCredential credential=devices.refreshCredential(license.id(),device.id(),challenge.challengeId(),"integration", "127.0.0.1");assertThrows(RuntimeException.class,()->devices.refreshCredential(license.id(),device.id(),challenge.challengeId(),"integration","127.0.0.1"));Challenge expired=devices.createChallenge(device.id(),"127.0.0.1");licensing.update("UPDATE device_challenges SET Expires_At=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE Id_Challenge=:id",Map.of("id",expired.challengeId()));assertThrows(RuntimeException.class,()->devices.verifyChallenge(device.id(),expired.challengeId(),sign(expired,android),"127.0.0.1"));return credential;}
	private String sign(Challenge challenge,KeyPair keys)throws Exception{Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(keys.getPrivate());signer.update(Base64.getUrlDecoder().decode(challenge.nonce()));return Base64.getEncoder().encodeToString(signer.sign());}
	private DeviceIdentity identity(String packageName,KeyPair keys){return new DeviceIdentity("Simulador Android",packageName,Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),"metadata-android-id","ETIC","SIM","14","1.0","TEE",false);}
	private KeyPair keyPair()throws Exception{KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));KeyPair pair=generator.generateKeyPair();if(integrationServerPublicKey==null)integrationServerPublicKey=pair.getPublic();return pair;}
	private KeyPair keyPairUnchecked(){try{return keyPair();}catch(Exception ex){throw new IllegalStateException(ex);}}
	private NamedParameterJdbcTemplate jdbc(String url,String user,String password){DriverManagerDataSource ds=new DriverManagerDataSource(url,user,password);ds.setDriverClassName("com.mysql.cj.jdbc.Driver");return new NamedParameterJdbcTemplate(ds);}
	private String env(String name,String fallback){String value=System.getenv(name);return value==null||value.isBlank()?fallback:value;}
}
