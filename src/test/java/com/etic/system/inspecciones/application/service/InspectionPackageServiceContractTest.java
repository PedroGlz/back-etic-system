package com.etic.system.inspecciones.application.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionPackageServiceContractTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final InspectionPackageService service = new InspectionPackageService(
        jdbc, null, "ETIC_PDM", null, new ObjectMapper(), null, new StorageProperties());

    private Object meta(List<String> columns) throws Exception {
        var type = Class.forName(InspectionPackageService.class.getName() + "$TableMeta");
        var constructor = type.getDeclaredConstructor(List.class, List.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(columns, List.of(), List.of("Id_Datos_Reporte"));
    }

    @Test
    void preservesReportJsonNullEmptyAndVisibilityWithoutAddingAbsentFields() throws Exception {
        String json = "[{\"granja\":\"México ñ\",\"corrientePorFase\":null},{\"granja\":\"\"}]";
        for (Integer flag : new Integer[]{null, 0, 1}) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("desglose_areas_inspeccionadas", json);
            input.put("incluir_desglose_areas", flag);
            input.put("direccion_reporte", "");
            Map<?, ?> result = ReflectionTestUtils.invokeMethod(service, "filterColumns", "datos_reporte", input,
                meta(List.of("desglose_areas_inspeccionadas", "incluir_desglose_areas", "direccion_reporte", "nombre_contacto")));
            assertEquals(input, result);
            assertFalse(result.containsKey("nombre_contacto"));
        }
    }

    @Test
    void rejectsUnknownOperationalColumnInsteadOfDiscardingIt() throws Exception {
        Object meta = meta(List.of("Id_Ubicacion"));
        assertThrows(BusinessValidationException.class, () -> ReflectionTestUtils.invokeMethod(
            service, "filterColumns", "ubicaciones", Map.of("Orden_Arbol", 3), meta));
    }

    @Test
    void preservesNullTreeOrderAndHierarchyFields() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("Orden_Arbol", null);
        input.put("Id_Ubicacion_Padre", "parent-id");
        input.put("Nivel_arbol", 2);
        assertEquals(input, ReflectionTestUtils.invokeMethod(service, "filterColumns", "ubicaciones", input,
            meta(List.of("Orden_Arbol", "Id_Ubicacion_Padre", "Nivel_arbol"))));
    }

    @Test
    void rejectsForeignSiteAndForeignReportInspection() {
        assertThrows(BusinessValidationException.class, () -> ReflectionTestUtils.invokeMethod(service,
            "validateRowOwnership", "ubicaciones", Map.of("Id_Sitio", "other"), "site", "inspection"));
        assertThrows(BusinessValidationException.class, () -> ReflectionTestUtils.invokeMethod(service,
            "validateRowOwnership", "datos_reporte", Map.of("Id_Inspeccion", "other"), "site", "inspection"));
    }

    @Test
    void preservesExistingSiteWideHistoryPolicy() {
        when(jdbc.queryForList("SELECT Id_Sitio FROM inspecciones WHERE Id_Inspeccion = ?", "old-inspection"))
            .thenReturn(List.of(Map.of("Id_Sitio", "site")));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateRowOwnership", "problemas",
            Map.of("Id_Sitio", "site", "Id_Inspeccion", "old-inspection"), "site", "current-inspection"));
    }

    @Test
    void partialZipPreservesAbsentColumnsAndTablesAcrossRepeatedImports() throws Exception {
        ZipHarness h = new ZipHarness();
        Map<String, Object> beforeLocation = new LinkedHashMap<>(h.location());
        var zip = h.zip(Map.of("inspection/datos_reporte.json", Map.of(
            "table", "datos_reporte", "rows", List.of(Map.of("Id_Datos_Reporte", "report", "direccion_reporte", "Nueva dirección")))));
        h.service.importInspectionResult("inspection", zip);
        h.service.importInspectionResult("inspection", zip);
        assertEquals("Nueva dirección", h.report().get("direccion_reporte"));
        assertEquals("Contacto previo", h.report().get("nombre_contacto"));
        assertEquals(beforeLocation, h.location());
        assertEquals(1, h.data.get("datos_reporte").size());
        assertTrue(h.writes.stream().allMatch(sql -> sql.startsWith("UPDATE datos_reporte") && !sql.contains("nombre_contacto =")));
    }

    @Test
    void zipPreservesExplicitNullEmptyZeroFalseAndBachoco() throws Exception {
        String json = "[{\"granja\":\"México ñ\",\"corrientePorFase\":null},{\"granja\":\"\"}]";
        for (Object flag : new Object[]{null, 0, false}) {
            ZipHarness h = new ZipHarness();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Id_Datos_Reporte", "report");
            row.put("nombre_contacto", null);
            row.put("direccion_reporte", "");
            row.put("incluir_desglose_areas", flag);
            row.put("desglose_areas_inspeccionadas", json);
            h.service.importInspectionResult("inspection", h.zip(Map.of("inspection/datos_reporte.json",
                Map.of("table", "datos_reporte", "rows", List.of(row)))));
            assertNull(h.report().get("nombre_contacto"));
            assertEquals("", h.report().get("direccion_reporte"));
            assertEquals(flag, h.report().get("incluir_desglose_areas"));
            assertEquals(json, h.report().get("desglose_areas_inspeccionadas"));
        }
    }

    @Test
    void zipPreservesTreeOrderAndHierarchyWithPartialRows() throws Exception {
        for (Integer order : new Integer[]{null, 0, 7}) {
            ZipHarness h = new ZipHarness();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Id_Ubicacion", "location");
            row.put("Orden_Arbol", order);
            h.service.importInspectionResult("inspection", h.zip(Map.of("context/ubicaciones.json",
                Map.of("table", "ubicaciones", "rows", List.of(row)))));
            assertEquals(order, h.location().get("Orden_Arbol"));
            assertEquals("parent", h.location().get("Id_Ubicacion_Padre"));
            assertEquals(2, h.location().get("Nivel_arbol"));
        }
    }

    @Test
    void zipAllowsNoOptionalFilesAndDoesNotWriteAnything() throws Exception {
        ZipHarness h = new ZipHarness();
        h.service.importInspectionResult("inspection", h.zip(Map.of()));
        assertTrue(h.writes.isEmpty());
        assertEquals("Contacto previo", h.report().get("nombre_contacto"));
    }

    @Test
    void zipDoesNotRequireAbsentSchemaColumnsButRejectsUnknownPresentData() throws Exception {
        ZipHarness h = new ZipHarness();
        h.columns.get("datos_reporte").remove("desglose_areas_inspeccionadas");
        h.service.importInspectionResult("inspection", h.zip(Map.of("inspection/datos_reporte.json",
            Map.of("table", "datos_reporte", "rows", List.of(Map.of("Id_Datos_Reporte", "report", "direccion_reporte", "OK"))))));
        var unsupported = h.zip(Map.of("inspection/datos_reporte.json", Map.of("table", "datos_reporte",
            "rows", List.of(Map.of("Id_Datos_Reporte", "report", "desglose_areas_inspeccionadas", "[]")))));
        assertThrows(BusinessValidationException.class, () -> h.service.importInspectionResult("inspection", unsupported));
        assertEquals(1, h.writes.size());
    }

    @Test
    void newPartialRowIsInsertedOnceAndReimportedWithoutDuplication() throws Exception {
        ZipHarness h = new ZipHarness();
        var zip = h.zip(Map.of("inspection/datos_reporte.json", Map.of("table", "datos_reporte", "rows",
            List.of(Map.of("Id_Datos_Reporte", "new-report", "Id_Inspeccion", "inspection", "direccion_reporte", "")))));
        h.service.importInspectionResult("inspection", zip);
        h.service.importInspectionResult("inspection", zip);
        assertEquals(2, h.data.get("datos_reporte").size());
        assertEquals(1, h.writes.stream().filter(sql -> sql.startsWith("INSERT")).count());
        assertEquals("", h.data.get("datos_reporte").get("new-report").get("direccion_reporte"));
    }

    @Test
    void emptyTemporalTextIsRejectedRatherThanSilentlyReplacedWithNull() throws Exception {
        ZipHarness h = new ZipHarness();
        var zip = h.zip(Map.of("inspection/datos_reporte.json", Map.of("table", "datos_reporte", "rows",
            List.of(Map.of("Id_Datos_Reporte", "report", "fecha_inicio_ra", "")))));
        assertThrows(BusinessValidationException.class, () -> h.service.importInspectionResult("inspection", zip));
        assertEquals("2026-11-01", h.report().get("fecha_inicio_ra"));
        assertTrue(h.writes.isEmpty());
    }

    @Test
    void checksumAndPathTableProtectionRemainEnabled() throws Exception {
        ZipHarness h = new ZipHarness();
        var invalidTable = h.zip(Map.of("inspection/datos_reporte.json", Map.of("table", "clientes", "rows", List.of())));
        assertThrows(BusinessValidationException.class, () -> h.service.importInspectionResult("inspection", invalidTable));
        var invalidChecksum = h.zip(Map.of(), true);
        assertThrows(BusinessValidationException.class, () -> h.service.importInspectionResult("inspection", invalidChecksum));
        assertTrue(h.writes.isEmpty());
    }

    // SQL calls are simulated: these tests do not connect to or execute SQL in any database.
    private static class ZipHarness {
        final Map<String, Map<String, Map<String, Object>>> data = new LinkedHashMap<>();
        final Map<String, java.util.ArrayList<String>> columns = new LinkedHashMap<>();
        final java.util.List<String> writes = new java.util.ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        final InspectionPackageService service;

        ZipHarness() {
            data.put("datos_reporte", new LinkedHashMap<>());
            data.put("ubicaciones", new LinkedHashMap<>());
            data.get("datos_reporte").put("report", new LinkedHashMap<>(Map.of(
                "Id_Datos_Reporte", "report", "Id_Inspeccion", "inspection", "Id_Sitio", "site",
                "nombre_contacto", "Contacto previo", "direccion_reporte", "Dirección anterior", "fecha_inicio_ra", "2026-11-01")));
            data.get("ubicaciones").put("location", new LinkedHashMap<>(Map.of(
                "Id_Ubicacion", "location", "Id_Sitio", "site", "Orden_Arbol", 4, "Id_Ubicacion_Padre", "parent", "Nivel_arbol", 2)));
            columns.put("datos_reporte", new java.util.ArrayList<>(List.of("Id_Datos_Reporte", "Id_Inspeccion", "Id_Sitio",
                "nombre_contacto", "direccion_reporte", "fecha_inicio_ra", "desglose_areas_inspeccionadas", "incluir_desglose_areas")));
            columns.put("ubicaciones", new java.util.ArrayList<>(List.of("Id_Ubicacion", "Id_Sitio", "Orden_Arbol", "Id_Ubicacion_Padre", "Nivel_arbol")));
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
            when(jdbc.queryForList(anyString())).thenAnswer(inv -> {
                String sql = inv.getArgument(0);
                if (sql.startsWith("SELECT table_name FROM export_table_scope")) return List.of(Map.of("table_name", "fabricantes"));
                String table = sql.substring("SHOW COLUMNS FROM ".length());
                return columns.get(table).stream().map(column -> {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("Field", column);
                    meta.put("Type", column.equals("fecha_inicio_ra") ? "date" : "text");
                    meta.put("Key", column.equals(pk(table)) ? "PRI" : "");
                    return meta;
                }).toList();
            });
            when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
                String sql = inv.getArgument(0);
                if (sql.startsWith("SELECT Id_Sitio FROM inspecciones")) return List.of(Map.of("Id_Sitio", "site"));
                String table = sql.split(" FROM ")[1].split(" ")[0];
                Object id = ((Object[]) inv.getRawArguments()[1])[0];
                Map<String, Object> row = data.get(table).get(id);
                return row == null ? List.of() : List.of(new LinkedHashMap<>(row));
            });
            var client = mock(org.springframework.jdbc.core.simple.JdbcClient.class);
            when(client.sql(anyString())).thenAnswer(inv -> {
                String sql = inv.getArgument(0);
                String table = sql.startsWith("UPDATE ") ? sql.split(" ")[1]
                    : sql.startsWith("INSERT INTO ") ? sql.split(" ")[2] : sql.split(" FROM ")[1].split(" ")[0];
                Map<String, Object> params = new LinkedHashMap<>();
                var statement = mock(org.springframework.jdbc.core.simple.JdbcClient.StatementSpec.class, RETURNS_DEEP_STUBS);
                when(statement.params(anyMap())).thenAnswer(call -> {
                    params.putAll(call.getArgument(0));
                    return statement;
                });
                when(statement.query(Integer.class).single()).thenAnswer(call -> data.get(table).containsKey(params.get("pk_" + pk(table))) ? 1 : 0);
                when(statement.update()).thenAnswer(call -> {
                    writes.add(sql);
                    Object id = params.containsKey("pk_" + pk(table)) ? params.get("pk_" + pk(table)) : params.get(pk(table));
                    Map<String, Object> row = data.get(table).computeIfAbsent((String) id, key -> new LinkedHashMap<>());
                    params.forEach((key, value) -> { if (!key.startsWith("pk_")) row.put(key, value); });
                    return 1;
                });
                return statement;
            });
            service = new InspectionPackageService(jdbc, null, "ETIC_PDM", client, mapper, null, new StorageProperties());
        }

        String pk(String table) { return table.equals("datos_reporte") ? "Id_Datos_Reporte" : "Id_Ubicacion"; }
        Map<String, Object> report() { return data.get("datos_reporte").get("report"); }
        Map<String, Object> location() { return data.get("ubicaciones").get("location"); }

        org.springframework.mock.web.MockMultipartFile zip(Map<String, Object> payloads) throws Exception { return zip(payloads, false); }
        org.springframework.mock.web.MockMultipartFile zip(Map<String, Object> payloads, boolean badChecksum) throws Exception {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            for (var entry : payloads.entrySet()) entries.put(entry.getKey(), mapper.writeValueAsBytes(entry.getValue()));
            entries.put("manifest.json", mapper.writeValueAsBytes(Map.of("package_type", "inspection_result", "delta_mode", "UPSERT_DELETE",
                "inspection", Map.of("id_inspeccion", "inspection"), "files", payloads.keySet())));
            Map<String, String> checksums = new LinkedHashMap<>();
            for (var entry : entries.entrySet()) checksums.put(entry.getKey(), "sha256:" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(entry.getValue())));
            if (badChecksum) checksums.put("manifest.json", "sha256:invalid");
            entries.put("checksums.json", mapper.writeValueAsBytes(checksums));
            var bytes = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
                for (var entry : entries.entrySet()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return new org.springframework.mock.web.MockMultipartFile("file", "synthetic.zip", "application/zip", bytes.toByteArray());
        }
    }
}