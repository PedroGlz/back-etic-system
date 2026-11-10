package com.etic.system.legacyimport.controller;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.model.LegacyImportError;
import com.etic.system.legacyimport.model.LegacyHomologation;
import com.etic.system.legacyimport.service.LegacyImportService;
import com.etic.system.legacyimport.service.LegacyEtlExecutionService;
import com.etic.system.legacyimport.service.LegacyHomologationService;
import com.etic.system.legacyimport.service.LegacyImportOperationException;
import com.etic.system.legacyimport.report.LegacyEtlReport;
import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/legacy-imports")
public class LegacyImportController {
	private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LegacyImportController.class);

	private final LegacyImportService service;
	private final LegacyEtlExecutionService executionService;
	private final LegacyHomologationService homologationService;

	public LegacyImportController(LegacyImportService service, LegacyEtlExecutionService executionService, LegacyHomologationService homologationService) {
		this.service = service;
		this.executionService = executionService;
		this.homologationService = homologationService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse upload(@RequestParam("file") MultipartFile file, HttpSession session) {
		AuthenticatedUser user = requireAdministrator(session);
		LegacyImportJob job = service.upload(file, user.id());
		return new UploadResponse(job.id(), job.status());
	}

	@GetMapping("/{id}/analysis")
	public LegacyImportAnalysis analysis(@PathVariable String id, HttpSession session) {
		return service.analysis(id, requireAdministrator(session).id());
	}

	@PostMapping("/{id}/execute")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void execute(@PathVariable String id, @org.springframework.web.bind.annotation.RequestBody(required = false) LegacyHomologation.Request request, HttpSession session) {
		String userId = requireAdministrator(session).id();
		if (request == null) {
			executionService.start(id, userId);
			return;
		}
		executionService.start(id, userId, homologationService.validate(id, userId, request, userId));
	}

	@GetMapping("/{id}/homologation")
	public LegacyHomologation.Review homologation(@PathVariable String id, @RequestParam(required = false) String inspectionId, HttpSession session) {
		return homologationService.review(id, requireAdministrator(session).id(), inspectionId);
	}

	@GetMapping("/active")
	public ResponseEntity<JobStatusResponse> active(HttpSession session){
		return service.active(requireAdministrator(session).id()).map(job->ResponseEntity.ok(statusResponse(job))).orElseGet(()->ResponseEntity.noContent().build());
	}

	@GetMapping("/{id}")
	public JobStatusResponse status(@PathVariable String id, HttpSession session) {
		LegacyImportJob job=service.status(id,requireAdministrator(session).id());
		return statusResponse(job);
	}

	@GetMapping("/{id}/result")
	public LegacyEtlReport result(@PathVariable String id,HttpSession session){
		return executionService.result(id,requireAdministrator(session).id());
	}

	@GetMapping(value="/{id}/report.md",produces="text/markdown;charset=UTF-8")
	public ResponseEntity<String> markdown(@PathVariable String id,HttpSession session){
		String report=executionService.markdownResult(id,requireAdministrator(session).id());
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=legacy-import-"+id+".md").body(report);
	}

	@GetMapping("/{id}/error")
	public LegacyImportError error(@PathVariable String id,HttpSession session){
		return service.error(id,requireTechnicalAdministrator().id());
	}

	@GetMapping(value="/{id}/error.txt",produces="text/plain;charset=UTF-8")
	public ResponseEntity<String> errorReport(@PathVariable String id,HttpSession session){
		LegacyImportError error=service.error(id,requireTechnicalAdministrator().id());
		String text="""
			REPORTE DE ERROR - IMPORTACIÓN HISTÓRICA ETIC
			Fecha y hora: %s
			Archivo de origen: %s
			Número de inspección: %s
			Etapa: %s
			Proceso: %s
			Estado: %s
			Mensaje: %s
			Causa principal: %s
			Tipo de excepción: %s

			Causas encadenadas:
			%s

			Detalle técnico:
			%s
			""".formatted(value(error.occurredAt()),value(error.filename()),value(error.inspectionNumber()),value(error.stage()),value(error.processId()),value(error.status()),value(error.message()),value(error.rootCause()),value(error.exceptionType()),String.join("\n",error.causes()),value(error.technicalDetail()));
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=error-importacion-"+id+".txt").body(text);
	}

	private AuthenticatedUser requireTechnicalAdministrator(){
		var user=com.etic.system.auth.security.WebIdentity.current();
		boolean administrator=user.systemAdmin()||user.roles().stream().anyMatch(role->role.equalsIgnoreCase("ADMIN")||role.equalsIgnoreCase("ADMINISTRADOR"));
		if(!administrator)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"El detalle técnico requiere permisos de administrador");
		return new AuthenticatedUser(user.id(),user.username(),user.firstName(),user.email(),null,null,null,null);
	}
	private String value(Object value){return value==null?"No disponible":String.valueOf(value);}

	private AuthenticatedUser requireAdministrator(HttpSession session) {
		var user=com.etic.system.auth.security.WebIdentity.current();
		if(user == null || user.id() == null || user.id().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere una sesión válida");
		}
		return new AuthenticatedUser(user.id(),user.username(),user.firstName(),user.email(),null,null,null,null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<LegacyImportError> legacyError(Exception exception,HttpServletRequest request){
		var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();var principal=authentication==null?null:authentication.getPrincipal();var user=principal instanceof com.etic.system.auth.security.WebIdentity identity?identity:null;boolean administrator=user!=null&&(user.systemAdmin()||user.roles().stream().anyMatch(role->role.equalsIgnoreCase("ADMIN")||role.equalsIgnoreCase("ADMINISTRADOR")));
		HttpStatus status=exception instanceof com.etic.system.shared.domain.exception.ResourceNotFoundException?HttpStatus.NOT_FOUND:exception instanceof ResponseStatusException response?HttpStatus.valueOf(response.getStatusCode().value()):exception instanceof com.etic.system.shared.domain.exception.BusinessValidationException||exception instanceof IllegalArgumentException||exception instanceof LegacyImportOperationException?HttpStatus.BAD_REQUEST:HttpStatus.INTERNAL_SERVER_ERROR;
		String processId=exception instanceof LegacyImportOperationException operation?operation.processId():processId(request.getRequestURI());String stage=exception instanceof LegacyImportOperationException operation?operation.stage():stage(request);
		Throwable diagnostic=exception instanceof LegacyImportOperationException operation&&operation.getCause()!=null?operation.getCause():exception;java.util.List<String> causes=new java.util.ArrayList<>();for(Throwable current=diagnostic;current!=null&&causes.size()<16;current=current.getCause()){String cause=sanitize(current.getMessage());if(cause!=null&&!cause.isBlank())causes.add(cause);}
		String specific=sanitize(exception.getMessage());if(specific==null||specific.isBlank())specific="No fue posible completar la operación de importación histórica";String root=causes.isEmpty()?specific:causes.getLast(),detail=null;
		if(administrator){java.io.StringWriter buffer=new java.io.StringWriter();diagnostic.printStackTrace(new java.io.PrintWriter(buffer));detail=sanitize(buffer.toString());}
		java.io.StringWriter serverBuffer=new java.io.StringWriter();diagnostic.printStackTrace(new java.io.PrintWriter(serverBuffer));log.error("LEGACY IMPORT - error stage={} process={} detail={}",stage,processId,sanitize(serverBuffer.toString()));
		String publicMessage=administrator?specific:publicMessage(exception,specific);LegacyImportError error=new LegacyImportError(processId,null,null,null,stage,status.name(),publicMessage,administrator?root:null,administrator?causes:java.util.List.of(),administrator?diagnostic.getClass().getName():errorCategory(exception),detail,java.time.LocalDateTime.now());
		return ResponseEntity.status(status).body(error);
	}
	private String processId(String uri){java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("/legacy-imports/([0-9A-Fa-f-]{36})").matcher(uri);return matcher.find()?matcher.group(1):null;}
	private String stage(HttpServletRequest request){String uri=request.getRequestURI();if(uri.endsWith("/homologation"))return "HOMOLOGATION";if(uri.endsWith("/execute"))return "INICIO";if(uri.endsWith("/analysis"))return "ANALYSIS";if(uri.endsWith("/result"))return "CONCILIATION";if(uri.endsWith("/report.md"))return "REPORT";return "UPLOAD";}
	private String errorCategory(Throwable error){if(error instanceof LegacyImportOperationException operation&&operation.getCause()!=null)return errorCategory(operation.getCause());if(error instanceof ResponseStatusException)return "AUTORIZACIÓN";if(error instanceof com.etic.system.shared.domain.exception.BusinessValidationException||error instanceof IllegalArgumentException)return "VALIDACIÓN";if(error instanceof org.springframework.dao.DataAccessException)return "BASE_DE_DATOS";if(error instanceof java.io.IOException)return "ARCHIVO";return "INTERNO";}
	private String publicMessage(Throwable error,String specific){if(error instanceof LegacyImportOperationException operation&&operation.getCause()!=null)return publicMessage(operation.getCause(),specific);if(error instanceof ResponseStatusException)return specific;if(error instanceof com.etic.system.shared.domain.exception.BusinessValidationException||error instanceof IllegalArgumentException)return specific;if(error instanceof org.springframework.dao.DataAccessException)return "No fue posible consultar o guardar la información de la importación";if(error instanceof java.io.IOException)return "No fue posible leer o guardar el archivo de importación";return "Ocurrió un error interno durante la importación histórica";}
	private String sanitize(String value){if(value==null)return null;return value.replaceAll("(?i)(password|passwd|token|authorization|jwt|secret|api[_-]?key|user(?:name)?)(\\s*[:=]\\s*)([^\\s,;]+)","$1$2[REDACTADO]").replaceAll("(?i)jdbc:[^\\s]+","[CONEXIÓN REDACTADA]");}

	public record UploadResponse(String id, LegacyImportStatus status) {
	}

	public record JobStatusResponse(String id,LegacyImportStatus status,String phase,int progress,String errorMessage) {}
	private JobStatusResponse statusResponse(LegacyImportJob job){return new JobStatusResponse(job.id(),job.status(),job.phase(),job.progress(),job.errorMessage());}
}
