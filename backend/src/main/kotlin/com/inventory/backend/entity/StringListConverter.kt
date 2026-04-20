package com.inventory.backend.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<MutableList<String>, String?> {
    override fun convertToDatabaseColumn(attribute: MutableList<String>?): String? =
        attribute
            ?.asSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.joinToString("\n")
            ?.ifBlank { null }

    override fun convertToEntityAttribute(dbData: String?): MutableList<String> =
        dbData
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toMutableList()
            ?: mutableListOf()
}
