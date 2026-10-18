package com.tgwizard.aide

/** Data Layer contract shared with the wear module (keep in sync with wear/.../Protocol.kt). */
object Protocol {
    const val PHONE_CAPABILITY = "aide_phone"
    const val PATH_LOCK = "/aide/lock"

    const val RESULT_OK = "ok"
    const val RESULT_NO_ACCESSIBILITY = "no_accessibility"
    const val RESULT_FAILED = "failed"
}
