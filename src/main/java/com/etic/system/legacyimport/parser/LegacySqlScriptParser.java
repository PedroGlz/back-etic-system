package com.etic.system.legacyimport.parser;

import com.etic.system.legacyimport.model.LegacyImportAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LegacySqlScriptParser {

	private static final Map<String, String> TABLE_DATASETS = tableDatasets();
	private final ObjectMapper mapper;

	public LegacySqlScriptParser(ObjectMapper mapper) { this.mapper = mapper; }

	public LegacyImportAnalysis analyze(InputStream input, long maxRecords) {
		LegacyImportAccumulator accumulator = new LegacyImportAccumulator(maxRecords);
		parse(input, (dataset, row) -> accumulator.accept(dataset, row));
		return accumulator.finishSql();
	}

	public void stage(Path source, Path directory) {
		try {
			Files.createDirectories(directory);
			Map<String, BufferedWriter> writers = new LinkedHashMap<>();
			try {
				for (String dataset : LegacyJsonContract.REQUIRED_DATASETS) {
					writers.put(dataset, Files.newBufferedWriter(directory.resolve(dataset + ".ndjson"), StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
				}
				try (InputStream input = Files.newInputStream(source)) {
					parse(input, (dataset, row) -> writeRow(writers.get(dataset), row));
				}
			} finally {
				for (BufferedWriter writer : writers.values()) writer.close();
			}
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("No fue posible preparar los datos del SQL legacy", exception);
		}
	}

	private void parse(InputStream input, RowConsumer consumer) {
		ParseContext context=new ParseContext(consumer);
		try (BufferedReader reader = reader(input)) {
			StringBuilder statement = new StringBuilder();
			boolean quoted = false;
			boolean lineComment = false;
			boolean blockComment = false;
			int previous = -1;
			int current;
			while ((current = reader.read()) != -1) {
				char c = (char) current;
				if (lineComment) { if (c == '\n') { lineComment = false; statement.append(c); } previous = current; continue; }
				if (blockComment) { if (previous == '*' && c == '/') blockComment = false; previous = current; continue; }
				if (!quoted && previous == '-' && c == '-') { if (statement.length() > 0) statement.setLength(statement.length() - 1); lineComment = true; previous = current; continue; }
				if (!quoted && previous == '/' && c == '*') { if (statement.length() > 0) statement.setLength(statement.length() - 1); blockComment = true; previous = current; continue; }
				statement.append(c);
				if (c == '\'' && quoted) {
					reader.mark(1); int next = reader.read();
					if (next == '\'') { statement.append('\''); previous = next; continue; }
					quoted = false; if (next != -1) reader.reset();
				} else if (c == '\'' && !quoted) quoted = true;
				if (!quoted && (c == 'T' || c == 't')) splitConsecutiveCommand(statement, context);
				if (!quoted && (c == 'O' || c == 'o')) splitGo(statement,context);
				if (c == ';' && !quoted) { parseStatement(statement.toString(), context); statement.setLength(0); }
				previous = current;
			}
			if (!statement.toString().isBlank()) parseStatement(statement.toString(), context);
			context.validateCoverage();
		} catch (IOException exception) {
			throw new LegacyJsonFormatException("No fue posible leer el SQL legacy", exception);
		}
	}

	private BufferedReader reader(InputStream input) throws IOException {
		PushbackInputStream detected = new PushbackInputStream(input, 3);
		byte[] prefix = new byte[3];
		int count = detected.read(prefix);
		Charset charset = StandardCharsets.UTF_8;
		int bomLength = 0;
		if (count >= 2 && prefix[0] == (byte) 0xFF && prefix[1] == (byte) 0xFE) {
			charset = StandardCharsets.UTF_16LE;
			bomLength = 2;
		} else if (count >= 2 && prefix[0] == (byte) 0xFE && prefix[1] == (byte) 0xFF) {
			charset = StandardCharsets.UTF_16BE;
			bomLength = 2;
		} else if (count >= 3 && prefix[0] == (byte) 0xEF && prefix[1] == (byte) 0xBB && prefix[2] == (byte) 0xBF) {
			bomLength = 3;
		}
		if (count > bomLength) detected.unread(prefix, bomLength, count - bomLength);
		return new BufferedReader(new InputStreamReader(detected, charset), 64 * 1024);
	}

	private void splitConsecutiveCommand(StringBuilder statement, ParseContext context) {
		int lineStart = Math.max(statement.lastIndexOf("\n"), statement.lastIndexOf("\r")) + 1;
		String line = statement.substring(lineStart).trim();
		if (!line.equalsIgnoreCase("INSERT")&&!line.equalsIgnoreCase("CREATE TABLE")) return;
		String previous = statement.substring(0, lineStart);
		if (findWord(previous,"INSERT")<0&&findWord(previous,"CREATE")<0)return;
		parseStatement(previous,context);
		statement.delete(0, lineStart);
	}
	private void splitGo(StringBuilder statement,ParseContext context){int lineStart=Math.max(statement.lastIndexOf("\n"),statement.lastIndexOf("\r"))+1;if(!statement.substring(lineStart).trim().equalsIgnoreCase("GO"))return;parseStatement(statement.substring(0,lineStart),context);statement.setLength(0);}

	private void parseStatement(String raw, ParseContext context) {
		int insert = findWord(raw, "INSERT");
		int create=findWord(raw,"CREATE");
		if(create>=0&&(insert<0||create<insert)){parseCreate(raw.substring(create),context);return;}
		if (insert < 0) return;
		Cursor cursor = new Cursor(raw.substring(insert));
		cursor.word("INSERT"); cursor.optionalWord("INTO");
		String table = cursor.qualifiedIdentifier();
		String dataset = TABLE_DATASETS.get(table.toLowerCase(Locale.ROOT));
		if(dataset==null)return;
		List<String> columns = cursor.nextIs('(')?cursor.identifiers():context.columns(table);
		cursor.word("VALUES");
		do {
			List<Object> values = cursor.values();
			context.found(dataset);
			if (columns.size() != values.size()) throw new LegacyJsonFormatException("Columnas y valores no coinciden en " + table);
			ObjectNode row = mapper.createObjectNode();
			for (int i = 0; i < columns.size(); i++) put(row, columns.get(i), values.get(i));
			context.accept(dataset,row);
		} while (cursor.consumeComma());
	}

	private void parseCreate(String raw,ParseContext context){Cursor cursor=new Cursor(raw);cursor.word("CREATE");cursor.word("TABLE");String table=cursor.qualifiedIdentifier();if(!TABLE_DATASETS.containsKey(table.toLowerCase(Locale.ROOT)))return;String body=cursor.parenthesizedBody();List<ColumnMetadata> columns=new ArrayList<>();for(String definition:splitDefinitions(body)){Cursor part=new Cursor(definition);String first=part.identifier();if(List.of("CONSTRAINT","PRIMARY","FOREIGN","UNIQUE","CHECK").contains(first.toUpperCase(Locale.ROOT)))continue;String type=part.typeName();String upper=definition.toUpperCase(Locale.ROOT);columns.add(new ColumnMetadata(first,type,!upper.contains("NOT NULL")));}if(columns.isEmpty())throw new LegacyJsonFormatException("CREATE TABLE sin columnas para "+table);context.schema(table,columns);}
	private List<String> splitDefinitions(String body){List<String> result=new ArrayList<>();int start=0,depth=0;boolean quoted=false;for(int i=0;i<body.length();i++){char c=body.charAt(i);if(c=='\'')quoted=!quoted;if(quoted)continue;if(c=='(')depth++;else if(c==')')depth--;else if(c==','&&depth==0){result.add(body.substring(start,i).trim());start=i+1;}}if(start<body.length())result.add(body.substring(start).trim());return result;}

	private void put(ObjectNode row, String column, Object value) {
		if (value == null) row.putNull(column);
		else if (value instanceof BigDecimal number) row.put(column, number);
		else row.put(column, value.toString());
	}

	private void writeRow(BufferedWriter writer, ObjectNode row) {
		try { writer.write(mapper.writeValueAsString(row)); writer.newLine(); }
		catch (IOException exception) { throw new LegacyJsonFormatException("No fue posible escribir un dataset legacy", exception); }
	}

	private int findWord(String text, String word) {
		String upper = text.toUpperCase(Locale.ROOT);
		for (int index = upper.indexOf(word); index >= 0; index = upper.indexOf(word, index + 1)) {
			boolean left = index == 0 || (!Character.isLetterOrDigit(upper.charAt(index - 1)) && upper.charAt(index - 1) != '_');
			int end = index + word.length(); boolean right = end == upper.length() || !Character.isLetterOrDigit(upper.charAt(end));
			if (end < upper.length() && upper.charAt(end) == '_') right = false;
			if (left && right) return index;
		}
		return -1;
	}

	private static Map<String, String> tableDatasets() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("customers", "customers"); values.put("customersites", "customerSites"); values.put("equipment", "equipment");
		values.put("equipmentgroups", "equipmentGroups"); values.put("equipmentfaultlinks", "equipmentFaultLinks"); values.put("manufacturers", "manufacturers");
		values.put("inspectiontypes", "inspectionTypes"); values.put("inspectionstati", "inspectionStatuses"); values.put("inspectiondetailstati", "inspectionDetailStatuses");
		values.put("inspections", "inspections"); values.put("inspectiondetails", "inspectionDetails"); values.put("locations", "locations");
		values.put("locationbaselines", "locationBaselines"); values.put("locationbaselinephotos", "locationBaselinePhotos"); values.put("problems", "problems");
		values.put("probleminspections", "problemInspections"); values.put("pieprobleminspections", "pieProblemInspections"); values.put("problemphotos", "problemPhotos");
		values.put("problemseverity", "problemSeverity"); values.put("prioritystati", "priorityStatus"); values.put("faults", "faults"); values.put("faulttypes", "faultTypes");
		values.put("rootcause", "rootCauses"); values.put("piephases", "piePhases"); values.put("pieenvironment", "pieEnvironments");
		return Map.copyOf(values);
	}

	@FunctionalInterface private interface RowConsumer { void accept(String dataset, ObjectNode row); }
	private record ColumnMetadata(String name,String sqlType,boolean nullable){}
	private static final class ParseContext{private final RowConsumer consumer;private final Map<String,List<ColumnMetadata>> schemas=new LinkedHashMap<>();private final Map<String,Long> found=new LinkedHashMap<>(),parsed=new LinkedHashMap<>();ParseContext(RowConsumer consumer){this.consumer=consumer;}void schema(String table,List<ColumnMetadata> columns){schemas.put(table.toLowerCase(Locale.ROOT),List.copyOf(columns));}List<String> columns(String table){List<ColumnMetadata> columns=schemas.get(table.toLowerCase(Locale.ROOT));if(columns==null)throw new LegacyJsonFormatException("INSERT sin columnas y sin CREATE TABLE previo para "+table);return columns.stream().map(ColumnMetadata::name).toList();}void found(String dataset){found.merge(dataset,1L,Long::sum);}void accept(String dataset,ObjectNode row){consumer.accept(dataset,row);parsed.merge(dataset,1L,Long::sum);}void validateCoverage(){found.forEach((dataset,count)->{long actual=parsed.getOrDefault(dataset,0L);if(count!=actual)throw new LegacyJsonFormatException("Cobertura incompleta en "+dataset+": INSERT="+count+", parseadas="+actual);});}}

	private static final class Cursor {
		private final String text; private int position;
		Cursor(String text) { this.text = text; }
		void word(String expected) { skip(); String actual = bare(); if (!expected.equalsIgnoreCase(actual)) fail("Se esperaba " + expected); }
		void optionalWord(String value) {
			int start = position; skip(); int end = position + value.length();
			if (end <= text.length() && text.regionMatches(true, position, value, 0, value.length())
				&& (end == text.length() || !Character.isLetterOrDigit(text.charAt(end)))) position = end;
			else position = start;
		}
		String qualifiedIdentifier() { String value = identifier(); skip(); if (peek('.')) { position++; value = identifier(); } return value; }
		boolean nextIs(char value){skip();return peek(value);}
		String parenthesizedBody(){skip();expect('(');int start=position,depth=1;boolean quoted=false;while(position<text.length()){char c=text.charAt(position++);if(c=='\'')quoted=!quoted;if(quoted)continue;if(c=='(')depth++;else if(c==')'&&--depth==0)return text.substring(start,position-1);}fail("CREATE TABLE sin cierre");return "";}
		String typeName(){skip();String base=identifier();skip();if(peek('(')){int start=position;int depth=0;do{char c=text.charAt(position++);if(c=='(')depth++;else if(c==')')depth--;}while(position<text.length()&&depth>0);return base+text.substring(start,position);}return base;}
		List<String> identifiers() { expect('('); List<String> result = new ArrayList<>(); do result.add(identifier()); while (consumeComma()); expect(')'); return result; }
		List<Object> values() { expect('('); List<Object> result = new ArrayList<>(); do result.add(value()); while (consumeComma()); expect(')'); return result; }
		boolean consumeComma() { skip(); if (peek(',')) { position++; return true; } return false; }
		String identifier() { skip(); if (peek('[')) { position++; int end = text.indexOf(']', position); if (end < 0) fail("Identificador sin cierre"); String result = text.substring(position, end); position = end + 1; return result; } return bare(); }
		private Object value() {
			skip();
			if (matchesWord("CAST")) return castValue();
			if ((peek('N') || peek('n')) && position + 1 < text.length() && text.charAt(position + 1) == '\'') position++;
			if (peek('\'')) return string();
			String token = token();
			if (token.equalsIgnoreCase("NULL")) return null;
			try { return new BigDecimal(token); } catch (NumberFormatException ignored) { return token; }
		}
		private Object castValue() {
			word("CAST"); expect('(');
			Object result = value();
			word("AS"); identifier();
			skip();
			if (peek('(')) { int depth = 0; do { char c = text.charAt(position++); if (c == '(') depth++; else if (c == ')') depth--; } while (position < text.length() && depth > 0); }
			expect(')');
			return result;
		}
		private boolean matchesWord(String value) { skip(); int end = position + value.length(); return end <= text.length() && text.regionMatches(true, position, value, 0, value.length()) && (end == text.length() || !Character.isLetterOrDigit(text.charAt(end))); }
		private String string() { position++; StringBuilder result = new StringBuilder(); while (position < text.length()) { char c = text.charAt(position++); if (c == '\'') { if (position < text.length() && text.charAt(position) == '\'') { result.append('\''); position++; } else return result.toString(); } else result.append(c); } fail("Cadena sin cierre"); return ""; }
		private String token() { skip(); int start = position; while (position < text.length() && ",)\r\n\t ".indexOf(text.charAt(position)) < 0) position++; if (start == position) fail("Valor vacío"); return text.substring(start, position); }
		private String bare() { skip(); int start = position; while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position)) || text.charAt(position) == '_')) position++; if (start == position) fail("Identificador inválido"); return text.substring(start, position); }
		private void expect(char expected) { skip(); if (!peek(expected)) fail("Se esperaba " + expected); position++; }
		private boolean peek(char value) { return position < text.length() && text.charAt(position) == value; }
		private void skip() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
		private void fail(String message) { throw new LegacyJsonFormatException(message + " cerca de posición " + position); }
	}
}
