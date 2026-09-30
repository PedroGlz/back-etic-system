package com.etic.system.licensing.application;

import com.etic.system.licensing.application.port.LicensingPersistencePort;
import com.etic.system.licensing.domain.*;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LicensingServiceTest {
	private LicensingPersistencePort persistence;
	private LicensingService service;
	private final LicensedDevice device = new LicensedDevice("D1","UUID","Tablet","ETIC","T1","14","ACTIVE","AUTO",null,null,null,"FINGERPRINT","EC","UNKNOWN",false,false,null,LocalDateTime.now(),LocalDateTime.now(),null,null,null,null,null);
	@BeforeEach void setUp(){persistence=mock(LicensingPersistencePort.class);service=new LicensingService(persistence);when(persistence.findDevice("D1")).thenReturn(Optional.of(device));}
	@Test void generatesApplicationCodeFromName(){assertGeneratedCode("ETIC Inspecciones","ETIC_INSPECCIONES");}
	@Test void removesApplicationCodeDiacritics(){assertGeneratedCode("ETIC Cámara Térmica","ETIC_CAMARA_TERMICA");}
	@Test void collapsesApplicationCodeSpaces(){assertGeneratedCode("  ETIC   Reportes  ","ETIC_REPORTES");}
	@Test void removesApplicationCodeSpecialCharacters(){assertGeneratedCode("ETIC / Inspección #1","ETIC_INSPECCION_1");}
	@Test void rejectsNameWithoutValidApplicationCode(){assertThrows(BusinessValidationException.class,()->service.createApplication(" / # ","pkg",LicensingMode.DEVICE_ONLY,"ACTIVE","ADMIN"));}
	@Test void resolvesApplicationCodeCollision(){when(persistence.applicationCodeExists(anyString(),isNull())).thenAnswer(i->i.getArgument(0).equals("ETIC_INSPECCIONES"));assertGeneratedCode("ETIC Inspecciones","ETIC_INSPECCIONES_2");}
	@Test void resolvesSecondApplicationCodeCollision(){when(persistence.applicationCodeExists(anyString(),isNull())).thenAnswer(i->List.of("ETIC_INSPECCIONES","ETIC_INSPECCIONES_2").contains(i.getArgument(0)));assertGeneratedCode("ETIC Inspecciones","ETIC_INSPECCIONES_3");}
	@Test void updateKeepsOriginalApplicationCode(){LicensedApplication original=app(LicensingMode.DEVICE_ONLY);when(persistence.findApplication("A1")).thenReturn(Optional.of(original));when(persistence.updateApplication(eq("A1"),anyString(),anyString(),any(),anyString(),anyString())).thenReturn(new LicensedApplication("A1",original.code(),"ETIC Inspecciones Termográficas","pkg",LicensingMode.DEVICE_ONLY,"ACTIVE",original.createdAt(),LocalDateTime.now()));LicensedApplication updated=service.updateApplication("A1","ETIC Inspecciones Termográficas","pkg",LicensingMode.DEVICE_ONLY,"ACTIVE","ADMIN");assertEquals("APP",updated.code());verify(persistence).updateApplication("A1","ETIC Inspecciones Termográficas","pkg",LicensingMode.DEVICE_ONLY,"ACTIVE","ADMIN");}

	@Test void userDeviceRequiresExistingUser(){when(persistence.findApplication("A1")).thenReturn(Optional.of(app(LicensingMode.USER_DEVICE)));assertThrows(BusinessValidationException.class,()->service.saveLicense(null,"A1","D1",null,LocalDate.now(),LocalDate.now().plusDays(30),LicenseStatus.ACTIVE,"ADMIN"));}
	@Test void userDeviceRejectsMissingUser(){when(persistence.findApplication("A1")).thenReturn(Optional.of(app(LicensingMode.USER_DEVICE)));when(persistence.activeUserExists("U1")).thenReturn(false);assertThrows(BusinessValidationException.class,()->service.saveLicense(null,"A1","D1","U1",LocalDate.now(),LocalDate.now().plusDays(30),LicenseStatus.ACTIVE,"ADMIN"));verify(persistence).activeUserExists("U1");}
	@Test void deviceOnlyDoesNotRequireUser(){when(persistence.findApplication("A1")).thenReturn(Optional.of(app(LicensingMode.DEVICE_ONLY)));when(persistence.saveLicense(anyString(),anyString(),anyString(),isNull(),any(),any(),eq(LicenseStatus.ACTIVE),anyString())).thenAnswer(i->license(i.getArgument(0),LicenseStatus.ACTIVE,LocalDate.now().plusDays(30),null,LicensingMode.DEVICE_ONLY));assertNotNull(service.saveLicense(null,"A1","D1",null,LocalDate.now(),LocalDate.now().plusDays(30),LicenseStatus.ACTIVE,"ADMIN"));verify(persistence,never()).activeUserExists(anyString());}
	@Test void deviceOnlyRejectsUser(){when(persistence.findApplication("A1")).thenReturn(Optional.of(app(LicensingMode.DEVICE_ONLY)));assertThrows(BusinessValidationException.class,()->service.saveLicense(null,"A1","D1","U1",LocalDate.now(),LocalDate.now().plusDays(30),LicenseStatus.ACTIVE,"ADMIN"));}
	@Test void rejectsUnknownApplication(){when(persistence.findApplication("X")).thenReturn(Optional.empty());assertThrows(ResourceNotFoundException.class,()->service.saveLicense(null,"X","D1",null,LocalDate.now(),LocalDate.now().plusDays(1),LicenseStatus.ACTIVE,"ADMIN"));}
	@Test void rejectsUnknownDevice(){when(persistence.findApplication("A1")).thenReturn(Optional.of(app(LicensingMode.DEVICE_ONLY)));when(persistence.findDevice("X")).thenReturn(Optional.empty());assertThrows(ResourceNotFoundException.class,()->service.saveLicense(null,"A1","X",null,LocalDate.now(),LocalDate.now().plusDays(1),LicenseStatus.ACTIVE,"ADMIN"));}
	@Test void expiredDateProducesExpiredLicense(){when(persistence.findApplication("A1")).thenReturn(Optional.of(app(LicensingMode.DEVICE_ONLY)));when(persistence.saveLicense(anyString(),anyString(),anyString(),isNull(),any(),any(),eq(LicenseStatus.EXPIRED),anyString())).thenAnswer(i->license(i.getArgument(0),LicenseStatus.EXPIRED,i.getArgument(5),null,LicensingMode.DEVICE_ONLY));assertEquals(LicenseStatus.EXPIRED,service.saveLicense(null,"A1","D1",null,LocalDate.now().minusDays(2),LocalDate.now().minusDays(1),LicenseStatus.ACTIVE,"ADMIN").status());}
	@Test void activateSuspendAndRevoke(){License active=license("L1",LicenseStatus.SUSPENDED,LocalDate.now().plusDays(30),null,LicensingMode.DEVICE_ONLY);when(persistence.findLicense("L1")).thenReturn(Optional.of(active),Optional.of(license("L1",LicenseStatus.ACTIVE,active.validUntil(),null,LicensingMode.DEVICE_ONLY)),Optional.of(active),Optional.of(license("L1",LicenseStatus.SUSPENDED,active.validUntil(),null,LicensingMode.DEVICE_ONLY)),Optional.of(active),Optional.of(license("L1",LicenseStatus.REVOKED,active.validUntil(),null,LicensingMode.DEVICE_ONLY)));assertEquals(LicenseStatus.ACTIVE,service.changeStatus("L1",LicenseStatus.ACTIVE,"A").status());assertEquals(LicenseStatus.SUSPENDED,service.changeStatus("L1",LicenseStatus.SUSPENDED,"A").status());assertEquals(LicenseStatus.REVOKED,service.changeStatus("L1",LicenseStatus.REVOKED,"A").status());}
	private void assertGeneratedCode(String name,String expected){when(persistence.createApplication(anyString(),anyString(),eq(name.trim()),eq("pkg"),eq(LicensingMode.DEVICE_ONLY),eq("ACTIVE"),eq("ADMIN"))).thenAnswer(i->new LicensedApplication(i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4),i.getArgument(5),LocalDateTime.now(),null));assertEquals(expected,service.createApplication(name,"pkg",LicensingMode.DEVICE_ONLY,"ACTIVE","ADMIN").code());}
	private LicensedApplication app(LicensingMode mode){return new LicensedApplication("A1","APP","App","pkg",mode,"ACTIVE",LocalDateTime.now(),null);}
	private License license(String id,LicenseStatus status,LocalDate until,String user,LicensingMode mode){return new License(id,"A1","APP","App",mode,"D1","UUID","Tablet",user,user,LocalDate.now(),until,status,"ACTIVE",status==LicenseStatus.ACTIVE?"READY":status.name(),LocalDateTime.now(),null);}
}
