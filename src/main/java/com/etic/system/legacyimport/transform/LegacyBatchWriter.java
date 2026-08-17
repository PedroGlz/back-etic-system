package com.etic.system.legacyimport.transform;

import java.util.List;
import java.util.Map;

public interface LegacyBatchWriter {

	void upsert(String targetTable, List<Map<String, Object>> rows);
}
