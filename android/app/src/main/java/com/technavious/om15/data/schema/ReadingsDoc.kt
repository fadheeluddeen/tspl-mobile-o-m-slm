package com.technavious.om15.data.schema

import org.json.JSONArray
import org.json.JSONObject

/** All data for one test, stored in readingsJson as {"fields":{...},"tables":{"<tableId>":[{...row...}]}}. */
class ReadingsDoc(
    val schema: TestSchema,
    val fields: Fields,
    val tables: MutableMap<String, MutableList<Row>>
) {
    fun rows(tableId: String): MutableList<Row> = tables.getOrPut(tableId) { mutableListOf() }

    fun findRow(tableId: String, rowId: String): Row? = tables[tableId]?.firstOrNull { it[ROW_ID] == rowId }

    fun recalc() {
        schema.tables.forEach { t -> rows(t.id).forEach { if (!it.isSectionHeader()) t.calc(it, fields) } }
        schema.fieldCalc(this)
    }

    fun groupNames(set: GroupSet): List<String> =
        set.tables.flatMap { rows(it) }.mapNotNull { it[set.key] }.distinct()

    fun addGroup(set: GroupSet, option: String = "") {
        val existing = groupNames(set)
        var i = existing.size
        var name = set.defaultName.withIndex(i).let { if (option.isNotEmpty()) it.replace("{type}", option) else it }
        while (name in existing && name.isNotEmpty()) { i++; name = set.defaultName.withIndex(i).replace("{type}", option) }
        set.tables.forEach { tid ->
            val table = schema.table(tid) ?: return@forEach
            val rows = rows(tid)
            set.template(tid, option).forEachIndexed { idx, extra ->
                rows += table.newRow(idx, fields, extra + (set.key to name))
            }
        }
        recalc()
    }

    fun renameGroup(set: GroupSet, old: String, new: String) {
        set.tables.forEach { tid -> rows(tid).forEach { if (it[set.key] == old) it[set.key] = new } }
    }

    fun deleteGroup(set: GroupSet, name: String) {
        set.tables.forEach { tid -> rows(tid).removeAll { it[set.key] == name } }
        recalc()
    }

    fun filledCount(): Pair<Int, Int> {
        var filled = 0; var total = 0
        schema.tables.forEach { table ->
            val inputs = table.columns.filter { it.isInput && it.type != ColType.IMAGE && it.type != ColType.CHECK }
            rows(table.id).filter { !it.isSectionHeader() }.forEach { row ->
                inputs.forEach { c -> total++; if (!row[c.key].isNullOrBlank()) filled++ }
            }
        }
        return filled to total
    }

    fun toJson(): String {
        val t = JSONObject()
        tables.forEach { (id, rows) -> t.put(id, JSONArray().apply { rows.forEach { put(JSONObject(it as Map<*, *>)) } }) }
        return JSONObject().put("fields", JSONObject(fields as Map<*, *>)).put("tables", t).toString()
    }

    companion object {
        fun parse(json: String?, schema: TestSchema): ReadingsDoc {
            val obj = runCatching { JSONObject(json ?: "{}") }.getOrDefault(JSONObject())
            val fields: Fields = mutableMapOf()
            obj.optJSONObject("fields")?.let { f -> f.keys().forEach { fields[it] = f.optString(it) } }
            val doc = ReadingsDoc(schema, fields, mutableMapOf())
            val tablesObj = obj.optJSONObject("tables")
            val isNew = tablesObj == null || obj.optJSONObject("fields") == null

            if (isNew) schema.fieldCols.forEach { c -> if (c.default.isNotEmpty() && fields[c.key].isNullOrEmpty()) fields[c.key] = c.default }

            tablesObj?.keys()?.forEach { id ->
                val arr = tablesObj.optJSONArray(id) ?: return@forEach
                val rows = doc.rows(id)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    rows += mutableMapOf<String, String>().apply { o.keys().forEach { k -> put(k, o.optString(k)) } }
                }
            }
            schema.tables.forEach { table ->
                val rows = doc.rows(table.id)
                if (table.presetRows.isNotEmpty() && rows.none { it.isFixed() }) {
                    table.presetRows.forEachIndexed { i, p -> rows += table.newRow(i, fields, p + (ROW_FIXED to "1")) }
                }
                if (isNew && rows.none { !it.isFixed() }) {
                    table.initialRows.forEachIndexed { i, p -> rows += table.newRow(i, fields, p) }
                }
            }
            doc.recalc()
            return doc
        }

        fun cellKey(tableId: String, rowId: String, colKey: String) = "$tableId|$rowId|$colKey"

        fun splitCellKey(key: String): Triple<String, String, String>? =
            key.split("|").takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) }
    }
}
