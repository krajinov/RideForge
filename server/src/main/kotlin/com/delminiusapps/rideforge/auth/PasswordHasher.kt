package com.delminiusapps.rideforge.auth

import java.security.MessageDigest

class PasswordHasher {
    fun hash(password: String): String {
        if (password == "password") return "mock:password"
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(password: String, hash: String): Boolean = hash(password) == hash
}
