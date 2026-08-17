-- Fixture SQL Server/SSMS: jamás debe ejecutarse.
USE [ETIC_LEGACY]
GO
SET ANSI_NULLS ON
GO
CREATE TABLE [dbo].[Customers] (
  [CustomerID] [char](38) NOT NULL,
  [Name] [nvarchar](50) NULL,
  [DeleteFlag] [tinyint] NULL,
  CONSTRAINT [PK_Customers] PRIMARY KEY ([CustomerID])
);
GO
INSERT INTO [dbo].[Customers] VALUES (N'C1',N'Cliente histórico',0);
GO
CREATE TABLE [dbo].[Locations] (
  [LocationID] [char](38) NOT NULL,
  [CustomerSiteID] [char](38) NULL,
  [ParentID] [char](38) NULL,
  [Name] [nvarchar](100) NULL,
  [InspectionOrder] [int] NULL,
  [IsEquipment] [bit] NULL,
  [DeleteFlag] [tinyint] NULL
);
GO
CREATE TABLE [dbo].[Inspections] (
  [InspectionID] [char](38) NOT NULL,
  [CustomerSiteID] [char](38) NULL,
  [CustomerID] [char](38) NULL,
  [InspectionStatusID] [char](38) NULL,
  [ScheduledStart] [datetime] NULL,
  [ScheduledEnd] [datetime] NULL
);
GO
CREATE TABLE [dbo].[Problems] (
  [ProblemID] [char](38) NOT NULL,
  [PriorProblemID] [char](38) NULL,
  [LocationID] [char](38) NULL,
  [IsChronic] [bit] NULL
);
GO
INSERT [dbo].[CustomerSites] ([CustomerSiteID],[CustomerID],[SiteName],[Address],[City],[State],[DefaultSiteFolder],[DeleteFlag]) VALUES ('S1','C1',N'Planta Norte',N'Calle 1',N'Monterrey',N'Nuevo León','PLANTA_NORTE',0);
INSERT INTO [dbo].[EquipmentGroups] ([EquipmentGroupID],[InspectionTypeID],[Name],[DeleteFlag]) VALUES ('EG1','IT1',N'Distribución',0);
INSERT INTO [dbo].[Equipment] ([EquipmentID],[EquipmentGroupID],[InspectionTypeID],[Name],[DeleteFlag]) VALUES ('E1','EG1','IT1',N'Tablero',0);
INSERT INTO [dbo].[Manufacturers] ([ManufacturerID],[InspectionTypeID],[Name],[DeleteFlag]) VALUES ('M1','IT1',N'Fabricante Uno',0);
INSERT INTO [dbo].[InspectionTypes] ([InspectionTypeID],[Name],[Description]) VALUES ('IT1','I/C Electrical',N'Eléctrica');
INSERT INTO [dbo].[InspectionStati] ([InspectionStatusID],[Name],[DeleteFlag]) VALUES ('IS1',N'Terminada',0);
INSERT INTO [dbo].[InspectionDetailStati] ([InspectionDetailStatusID],[Name],[DeleteFlag]) VALUES ('IDS1',N'Inspeccionada',0);
INSERT INTO [dbo].[PriorityStati] ([PriorityStatusID],[Name],[Description],[DeleteFlag]) VALUES ('PS1','CTO',N'Crítica',0);
INSERT INTO [dbo].[FaultTypes] ([FaultTypeID],[InspectionTypeID],[Name],[DeleteFlag]) VALUES ('FT1','IT1',N'Eléctrica',0);
INSERT INTO [dbo].[Faults] ([FaultID],[FaultTypeID],[Fault],[DeleteFlag]) VALUES ('F1','FT1',N'Conexión deficiente',0);
INSERT INTO [dbo].[EquipmentFaultLinks] ([FaultLinkID],[EquipmentID],[FaultID]) VALUES ('EFL1','E1','F1');
INSERT INTO [dbo].[RootCause] ([RootCauseID],[InspectionTypeID],[FaultID],[RootCause]) VALUES ('RC1','IT1','F1',N'Conexión floja');
INSERT INTO [dbo].[PIEPhases] ([PIEPhaseID],[Name],[DeleteFlag]) VALUES ('PH1',N'Fase A',0);
INSERT INTO [dbo].[ProblemSeverity] ([ProblemSeverityID],[Name],[BaseCentigrade],[BaseCRange]) VALUES ('SV1','1-Critical',16.5,N'>16 °C');
INSERT INTO [dbo].[PIEEnvironment] ([PIEEnvironmentID],[Name],[Adjust]) VALUES ('ENV1',N'Interior',0);
INSERT INTO [dbo].[Locations] ([LocationID],[CustomerSiteID],[ParentID],[Name],[InspectionOrder],[IsEquipment],[DeleteFlag]) VALUES ('L1','S1',NULL,N'Planta',1,0,0);
INSERT INTO [dbo].[Locations]
 ([LocationID],[CustomerSiteID],[ParentID],[Name],[PriorityStatusID],[InspectionTypeID],[EquipmentID],[ManufacturerID],[InspectionOrder],[IsEquipment],[LocationPath],[DeleteFlag])
 VALUES ('L2','S1','L1',N'Juan''s Equipment','PS1','IT1','E1','M1',1,1,N'Planta/Tablero principal',0);
INSERT INTO [dbo].[Locations] ([LocationID],[CustomerSiteID],[ParentID],[Name],[InspectionOrder],[IsEquipment],[LocationPath],[DeleteFlag]) VALUES ('L3','S1','L2',N'Interruptor principal',1,0,N'Planta/Tablero principal/Interruptor principal',0);
INSERT INTO [dbo].[Inspections] ([InspectionID],[CustomerSiteID],[CustomerID],[InspectionStatusID],[ScheduledStart],[ScheduledEnd],[NoOfDays],[TemperatureUnit],[InspectionNo],[DeleteFlag]) VALUES ('I1','S1','C1','IS1','2024-01-10 08:00:00','2024-01-10 17:00:00',1,'F',1,0),('I2','S1','C1','IS1','2025-01-10 08:00:00','2025-01-10 17:00:00',1,'C',2,0);
INSERT INTO [dbo].[InspectionDetails] ([InspectionDetailID],[InspectionID],[LocationID],[InspectionDetailStatusID],[CreateDate]) VALUES ('ID1','I1','L2','IDS1','2024-01-10T09:00:00'),('ID2','I2','L2','IDS1','2025-01-10T09:00:00');
INSERT INTO [dbo].[LocationBaselines] ([BaselineID],[LocationID],[InspectionID],[MeasuredBaseline],[AmbientBaseline],[CurrentThreshold],[CustomerNotes],[DeleteFlag]) VALUES ('B1','L2','I1',86,77,104,N'Base F',0);
INSERT INTO [dbo].[LocationBaselinePhotos] ([BaselinePhotoID],[BaselineID],[FileName],[CreateDate]) VALUES ('BP1','B1','baseline-ir.jpg','2024-01-10T10:00:00');
INSERT INTO [dbo].[Problems] ([ProblemID],[PriorProblemID],[LocationID],[EquipmentID],[FaultID],[InspectionTypeID],[ManufacturerID],[ComponentComment],[ProblemStatus],[IsChronic],[DeleteFlag]) VALUES ('P0',NULL,'L2','E1','F1','IT1','M1',N'Problema normal',1,0,0),('P1',NULL,'L2','E1','F1','IT1','M1',N'Crónico original',1,1,0),('P2','P1','L2','E1','F1','IT1','M1',N'Crónico recurrente',1,1,0);
INSERT INTO [dbo].[ProblemInspections] ([ProblemInspectionID],[ProblemID],[InspectionID],[InspectionDetailID],[ProblemNo],[CreateDate],[DeleteFlag]) VALUES ('PI0','P0','I1','ID1',1,'2024-01-10T11:00:00',0),('PI1','P1','I1','ID1',2,'2024-01-10T12:00:00',0),('PI2','P2','I2','ID2',1,'2025-01-10T12:00:00',0);
INSERT INTO [dbo].[PIEProblemInspections] ([PIEProblemInspectionID],[ProblemInspectionID],[ProblemTemperature],[ReferenceTemperature],[AmbientTemperature],[ProblemSeverityID],[PIEEnvironmentID],[CreateDate]) VALUES ('PIE0','PI0',140,86,77,'SV1','ENV1','2024-01-10T11:05:00'),('PIE1','PI1',122,86,77,'SV1','ENV1','2024-01-10T12:05:00'),('PIE2','PI2',52,30,25,'SV1','ENV1','2025-01-10T12:05:00');
INSERT INTO [dbo].[ProblemPhotos] ([ProblemPhotoID],[ProblemInspectionID],[PhotoFileName],[IRFilename],[CreateDate]) VALUES ('PP1','PI1','problema.jpg','problema-ir.jpg','2024-01-10T12:10:00');
INSERT INTO [dbo].[IgnoredAudit] ([ID],[Payload]) VALUES ('00000000-0000-0000-0000-000000000001',N'ignorar');
SET IDENTITY_INSERT [dbo].[Customers] OFF
GO
