package com.sleekydz86.namuwikingestion.dataclass

data class NamuwikiRow(
    val title: String,
    val text: String,
    val namespace: String? = null,
    val contributors: String? = null,
)
