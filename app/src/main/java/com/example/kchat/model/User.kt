package com.example.kchat.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String? = null,
    val about: String? = null
)

fun matchesUserSearch(userName: String, query: String): Boolean {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isEmpty()) return true
    val nameLower = userName.lowercase()
    if (nameLower.contains(cleanQuery)) return true
    val tokens = cleanQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    return tokens.all { token -> nameLower.contains(token) }
}
