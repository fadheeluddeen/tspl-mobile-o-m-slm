package com.technavious.om15.data.schema

import org.json.JSONArray
import org.json.JSONObject

/** Table rows for one test, stored in readingsJson as {"tables":{"<tableId>":[{...row...}]}}. */
class ReadingsDoc(val tables: MutableMap<String, MutableList<Row>>) {

    fun rows(tableId: String): MutableList<Row> = tables.getOrPut(tableId) { mutableListOf() }

    fun findRow(tableId: String, rowId: String): Row? = tables[tableId]?.firstOrNull { it[ROW_ID] == rowId }

    fun toJson(): String {
        val t = JSONObject()
        tables.forEach { (id, rows) ->
            t.put(id, JSONArray().apply { rows.forEach { put(JSONObject(it as Map<*, *>)) } })
        }
        return JSONObject().put("tables", t).toString()
    }

    fun filledCount(schema: TestSchema): Pair<Int, Int> {
        var filled = 0; var total = 0
        schema.tables.forEach { table ->
            val inputs = table.columns.filter { it.isInput }
            tables[table.id].orEmpty().forEach { row ->
                inputs.forEach { c -> total++; if (!row[c.key].isNullOrBlank()) filled++ }
            }
        }
        return filled to total
    }

    companion object {
        fun parse(json: String?, schema: TestSchema): ReadingsDoc {
            val doc = ReadingsDoc(mutableMapOf())
            val obj = runCatching { JSONObject(json ?: "{}") }.getOrDefault(JSONObject())
            val tablesObj = obj.optJSONObject("tables")
            if (tablesObj != null) {
                tablesObj.keys().forEach { id ->
                    val arr = tablesObj.optJSONArray(id) ?: return@forEach
                    val rows = doc.rows(id)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        rows += mutableMapOf<String, String>().apply { o.keys().forEach { k -> put(k, o.optString(k)) } }
                    }
                }
            } else if (obj.length() > 0) {
                // Readings saved before tables existed: treat the flat values as the first row.
                val first = schema.tables.first()
                val row = first.newRow(0)
                obj.keys().forEach { k -> if (first.col(k) != null) row[k] = obj.optString(k) }
                if (hasInput(first.columns, row)) { first.calc(row); doc.rows(first.id) += row }
            }
            schema.tables.forEach { table -> doc.rows(table.id).forEach(table.calc) }
            return doc
        }

        private fun hasInput(cols: List<Col>, row: Row) = cols.any { it.isInput && !row[it.key].isNullOrBlank() }

        /** Photo/camera key for one cell. */
        fun cellKey(tableId: String, rowId: String, colKey: String) = "$tableId|$rowId|$colKey"

        fun splitCellKey(key: String): Triple<String, String, String>? =
            key.split("|").takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) }
    }
}
