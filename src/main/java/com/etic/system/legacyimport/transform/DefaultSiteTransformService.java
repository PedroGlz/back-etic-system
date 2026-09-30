package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DefaultSiteTransformService implements SiteTransformService {

	@Override public void transform(LegacyEtlContext c) {
		Map<String,List<String>> existing = new HashMap<>();
		if (c.repository().tableExists("grupos_sitios")) {
			for (Map<String,Object> row : c.repository().query("SELECT Id_Grupo_Sitios, Id_Cliente FROM grupos_sitios WHERE Estatus='Activo'"))
				existing.computeIfAbsent(String.valueOf(row.get("Id_Cliente")), ignored -> new ArrayList<>()).add(String.valueOf(row.get("Id_Grupo_Sitios")));
		}
		Map<String,String> groupByCustomer = new LinkedHashMap<>();
		Map<String,Map<String,Object>> groups = new LinkedHashMap<>();
		c.forEach("customerSites", site -> {
			String customerId=c.text(site,"CustomerID");
			if (customerId == null) return;
			String groupId=groupByCustomer.computeIfAbsent(customerId, id -> existing.getOrDefault(id,List.of()).size()==1
				? existing.get(id).getFirst() : deterministic("ETIC:LEGACY:GROUP:"+id));
			if (existing.getOrDefault(customerId,List.of()).size()!=1) groups.putIfAbsent(groupId, c.row(
				"Id_Grupo_Sitios", groupId, "Id_Cliente", customerId, "Grupo", "Legacy", "Estatus", "Activo"));
		});
		if (!groups.isEmpty()) {
			List<Map<String,Object>> rows=new ArrayList<>(groups.values());
			rows.forEach(ignored -> c.report().source("grupos_sitios"));
			c.flush("grupos_sitios","Id_Grupo_Sitios",rows);
		}
		c.shared().put("groupByCustomer", groupByCustomer);
		c.transform("customerSites", "sitios", "Id_Sitio", r -> c.row(
			"Id_Sitio", c.text(r,"CustomerSiteID"), "Id_Cliente", c.text(r,"CustomerID"),
			"Id_Grupo_Sitios", groupByCustomer.get(c.text(r,"CustomerID")), "Sitio", c.text(r,"SiteName"),
			"Desc_Sitio", c.text(r,"Description"), "Direccion", c.text(r,"Address"), "Estado", c.text(r,"State"),
			"Municipio", c.text(r,"City"), "Folder", c.text(r,"DefaultSiteFolder"),
			"Estatus", inactive(r)?"Inactivo":"Activo",
			"Creado_Por", c.text(r,"CreateUserID"), "Fecha_Creacion", c.value(r,"CreateDate"),
			"Modificado_Por", c.text(r,"LastUserID"), "Fecha_Mod", c.value(r,"LastModified")));
		if (c.repository().tableExists("sitio_contactos")) {
			Map<String,String> existingContactBySite = new HashMap<>();
			for (Map<String,Object> row : c.repository().query(
				"SELECT Id_Sitio, Id_Sitio_Contacto FROM sitio_contactos ORDER BY Orden, Id_Sitio_Contacto")) {
				existingContactBySite.putIfAbsent(
					String.valueOf(row.get("Id_Sitio")),
					String.valueOf(row.get("Id_Sitio_Contacto"))
				);
			}
			c.transform("customerSites", "sitio_contactos", "Id_Sitio_Contacto", r -> {
				String siteId = c.text(r,"CustomerSiteID");
				String name = c.text(r,"ContactName");
				String role = c.text(r,"ContactTitle");
				if (siteId == null || existingContactBySite.containsKey(siteId) || (name == null && role == null)) return null;
				return c.row(
					"Id_Sitio_Contacto", deterministic("ETIC:LEGACY:SITE_CONTACT:" + siteId + ":1"),
					"Id_Sitio", siteId,
					"Nombre", name,
					"Puesto", role,
					"Estatus", inactive(r)?"Inactivo":"Activo",
					"Orden", 1
				);
			});
		}
	}
	private String deterministic(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().toUpperCase(); }
	private boolean inactive(Map<String,Object> row) { Object value=row.get("DeleteFlag"); return value != null && !"0".equals(value.toString()); }
}
