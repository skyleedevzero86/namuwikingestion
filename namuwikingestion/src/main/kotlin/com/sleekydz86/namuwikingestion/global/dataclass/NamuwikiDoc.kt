package com.sleekydz86.namuwikingestion.dataclass

data class NamuwikiDoc(
    val title: String,
    val content: String,
    val embedding: FloatArray,
    val namespace: String? = null,
    val contributors: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NamuwikiDoc
        if (title != other.title) return false
        if (content != other.content) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (namespace != other.namespace) return false
        if (contributors != other.contributors) return false
        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (namespace?.hashCode() ?: 0)
        result = 31 * result + (contributors?.hashCode() ?: 0)
        return result
    }
}
