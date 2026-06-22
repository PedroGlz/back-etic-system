package com.etic.system.catalogos.usuarios;

import com.etic.system.catalogos.shared.domain.model.CatalogDefinition;
import com.etic.system.catalogos.shared.domain.model.CatalogModule;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.field;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.passwordField;
import static com.etic.system.catalogos.shared.domain.model.CatalogDefinitions.referenceField;

@Component
public class UsuariosCatalogModule implements CatalogModule {

	@Override
	public String key() {
		return "usuarios";
	}

	@Override
	public CatalogDefinition definition() {
		return CatalogDefinition.of(
			"usuarios", "Usuarios", "usuarios", "Id_Usuario", "name", true,
			List.of(
				referenceField("groupId", "Grupo", true, "grupos"),
				field("username", "Usuario", true, 50),
				field("name", "Nombre", true, 100),
				passwordField(),
				field("email", "Correo electrónico", true, 300),
				field("phone", "Teléfono", false, 15),
				field("certificationLevel", "Nivel de certificación", false, 50)
			),
			"id", "Id_Usuario", "groupId", "Id_Grupo", "username", "Usuario", "name", "Nombre",
			"password", "Password", "email", "Email", "phone", "Telefono",
			"certificationLevel", "nivelCertificacion", "status", "Estatus"
		);
	}
}
