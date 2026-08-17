package com.etic.system.legacyimport.transform;

import org.springframework.stereotype.Component;

@Component
public class DefaultCustomerTransformService implements CustomerTransformService {
	@Override public void transform(LegacyEtlContext c) {
		c.transform("customers", "clientes", "Id_Cliente", r -> c.row(
			"Id_Cliente", c.text(r,"CustomerID"), "Razon_Social", c.text(r,"Name"), "Nombre_Comercial", c.text(r,"Name"),
			"Direccion", c.text(r,"Address"), "Estado", c.text(r,"State"), "Municipio", c.text(r,"City"),
			"Contacto_1", c.text(r,"ContactName"), "Puesto_Contacto_1", c.text(r,"ContactTitle"),
			"Estatus", inactive(r) ? "Inactivo" : "Activo", "Creado_Por", c.text(r,"CreateUserID"),
			"Fecha_Creacion", c.value(r,"CreateDate"), "Modificado_Por", c.text(r,"LastUserID"), "Fecha_Mod", c.value(r,"LastModified")));
	}
	private boolean inactive(java.util.Map<String,Object> row) { Object value=row.get("DeleteFlag"); return value != null && !"0".equals(value.toString()); }
}
