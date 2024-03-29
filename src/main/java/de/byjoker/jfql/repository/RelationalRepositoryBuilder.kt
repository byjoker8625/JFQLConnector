package de.byjoker.jfql.repository

import com.google.gson.Gson
import de.byjoker.jfql.connection.Connection
import de.byjoker.jfql.exception.RepositoryException
import de.byjoker.jfql.statement.*
import de.byjoker.jfql.util.Response
import de.byjoker.jfql.util.Result
import de.byjoker.jfql.util.TableEntry
import de.byjoker.jfql.util.TableType
import org.json.XMLTokener
import java.lang.reflect.Field
import java.util.Arrays.stream
import java.util.function.Consumer
import java.util.stream.Collectors


abstract class RelationalRepositoryBuilder<T>(
    private val connection: Connection,
    private val clazz: Class<T>,
    private var table: TableData? = null
) : Repository<T> {

    private val gson: Gson = Gson()

    fun build() {
        if (table != null) {
            throw RepositoryException("Repository already has been built!")
        }

        val table = clazz.getAnnotation(DatabaseTable::class.java)
        val fields: List<ColumnData> = getColumnFields()

        if (table.type != TableType.RELATIONAL) {
            throw RepositoryException("JFQLRepositories only work with relational tables!")
        }

        val primaryField = fields.firstOrNull { columnData -> columnData.primary }

        if (table.primary == "%FIELDS%" && primaryField == null) {
            throw RepositoryException("No primary key found in repository!")
        }

        val name = if (table.name == "%CLASS%") clazz.name else table.name
        val primary = if (table.primary == "%FIELDS%") primaryField?.name else table.primary
        val structure =
            if (table.structure == "%FIELDS%") fields.map { column -> column.name }.toMutableList().toString()
                .replace("[", "").replace("]", "").replace(", ", "") else table.structure

        if (primary == null) {
            throw RepositoryException("No primary key found in repository!")
        }

        connection.query(
            "create table '$name' structure '$structure' primary-key '$primary'",
            false
        )

        this.table = TableData(name, primary, structure, table.type)
    }

    private fun getColumnFields(entity: T): List<ColumnData> {
        val columns: MutableList<ColumnData> = ArrayList()

        stream(XMLTokener.entity.javaClass.declaredFields).filter { field: Field ->
            field.isAnnotationPresent(
                DatabaseColumn::class.java
            )
        }.collect(Collectors.toList()).forEach(
            Consumer { field: Field ->
                val column = field.getAnnotation(DatabaseColumn::class.java)
                field.isAccessible = true

                val name = if (column.name == "%VAR%") field.name else column.name
                val primary: Boolean = column.primary
                val json: Boolean = column.json

                val content = try {
                    if (json) {
                        gson.toJson(field[entity])
                    } else {
                        field[XMLTokener.entity]?.toString()
                    }
                } catch (ex: Exception) {
                    null
                }

                columns.add(ColumnData(name, content, json, primary))
            })

        return columns
    }

    private fun getColumnFields(): List<ColumnData> {
        val columns: MutableList<ColumnData> = ArrayList()

        stream(clazz.declaredFields).filter { field1: Field ->
            field1.isAnnotationPresent(
                DatabaseColumn::class.java
            )
        }.forEach { field: Field ->
            val column = field.getAnnotation(DatabaseColumn::class.java)

            columns.add(
                ColumnData(
                    if (column.name == "%") field.name else column.name,
                    null,
                    column.primary,
                    column.json
                )
            )
        }

        return columns
    }


    private fun buildInsertStatement(entity: T): InsertBuilder {
        val fields = getColumnFields(entity)
        val builder = InsertBuilder(table!!.name)

        val keys = StringBuilder("key")
        val values = StringBuilder("value")

        for (field in fields) {
            keys.append(" ").append(field.name)
            values.append(" ").append(field.content ?: "null")
        }

        builder.keys(keys.toString())
        builder.values(values.toString())

        return builder
    }

    private fun readPrimaryContent(entity: T): String? {
        val field = getColumnFields(entity).firstOrNull { columnData -> columnData.primary } ?: return null
        return field.content
    }

    override fun formatEntities(entries: MutableList<TableEntry>): MutableList<T> {
        return entries.map { column -> formatEntity(column) }.toMutableList()
    }

    override fun query(statement: Statement): Response {
        return connection.query(statement)
    }

    override fun saveAll(entities: MutableList<T>) {
        entities.forEach { entity -> save(entity) }
    }

    override fun save(entity: T) {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        connection.query(buildInsertStatement(entity).build())
    }

    override fun update(entity: T) {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        connection.query(buildInsertStatement(entity).primary(readPrimaryContent(entity).toString()).build())
    }

    override fun updateBy(entity: T, field: String, value: Any) {
        updateWhere(entity, ConditionSetBuilder(ConditionBuilder(field).`is`().equals(value).build()).build())
    }

    override fun updateWhere(entity: T, conditions: String) {
        updateWhere(entity, LegacyConditionSetBuilder(conditions).build())
    }

    override fun updateWhere(entity: T, conditionSet: ConditionSet) {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        connection.query(buildInsertStatement(entity).where(conditionSet).build())
    }

    override fun delete(entity: T) {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        connection.query(RemoveBuilder(readPrimaryContent(entity).toString()).from(table!!.name).build())
    }

    override fun deleteAll() {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        connection.query(RemoveBuilder("*").from(table!!.name).build())
    }

    override fun deleteAllBy(field: String, value: Any) {
        deleteAllWhere(ConditionSetBuilder(ConditionBuilder(field).`is`().equals(value).build()).build())
    }

    override fun deleteAllWhere(conditions: String) {
        deleteAllWhere(LegacyConditionSetBuilder(conditions).build())
    }

    override fun deleteAllWhere(conditionSet: ConditionSet) {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        connection.query(RemoveBuilder("*").where(conditionSet).build())
    }

    override fun findOneBy(field: String, value: Any): T? {
        return findOneWhere(ConditionSetBuilder(ConditionBuilder(field).`is`().equals(value).build()).build())
    }

    override fun findOneByPrimary(primary: Any): T? {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        return (connection.query(
            SelectBuilder("*").from(table!!.name).primary(primary.toString()).build()
        ) as Result).entries.map { tableEntry -> formatEntity(tableEntry) }.firstOrNull()
    }

    override fun findOneWhere(conditions: String): T? {
        return findOneWhere(LegacyConditionSetBuilder(conditions).build())
    }

    override fun findOneWhere(conditionSet: ConditionSet): T? {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        return (connection.query(
            SelectBuilder("*").from(table!!.name).where(conditionSet).build(),
            false
        ) as Result).entries.map { tableEntry -> formatEntity(tableEntry) }.firstOrNull()
    }

    override fun findAll(): MutableList<T> {
        return findAll(SelectBuilder("*").from(table!!.name))
    }

    override fun findAll(statement: SelectBuilder): MutableList<T> {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        return (connection.query(
            statement.build()
        ) as Result).entries.map { tableEntry -> formatEntity(tableEntry) }.toMutableList()
    }

    override fun findAllBy(field: String, value: Any): MutableList<T> {
        return findAllWhere(ConditionSetBuilder(ConditionBuilder(field).`is`().equals(value).build()).build())
    }

    override fun findAllWhere(conditions: String): MutableList<T> {
        return findAllWhere(LegacyConditionSetBuilder(conditions).build())
    }

    override fun findAllWhere(conditionSet: ConditionSet): MutableList<T> {
        if (table == null) {
            throw RepositoryException("Repository have to been built with 'super.build();'!");
        }

        return (connection.query(
            SelectBuilder("*").from(table!!.name).where(conditionSet).build()
        ) as Result).entries.map { tableEntry -> formatEntity(tableEntry) }.toMutableList()
    }

}
