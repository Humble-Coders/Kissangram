package com.kissangram

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform