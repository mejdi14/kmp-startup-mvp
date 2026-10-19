package com.mejdi14.kmpstartupmvp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform