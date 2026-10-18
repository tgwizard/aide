package com.tgwizard.aide

/** Data Layer contract shared with the phone module (keep in sync with phone/.../Protocol.kt). */
object Protocol {
    const val PHONE_CAPABILITY = "aide_phone"
    const val PATH_LOCK = "/aide/lock"
    const val PATH_STATUS = "/aide/status"
    const val PATH_SCREEN = "/aide/screen"
    const val PATH_DOOR_STATUS = "/aide/door/status"
    const val PATH_DOOR_LOCK = "/aide/door/lock"
    const val PATH_DOOR_UNLOCK = "/aide/door/unlock"
    const val PATH_HOME = "/aide/home"

    const val RESULT_OK = "ok"
    const val RESULT_NO_ACCESSIBILITY = "no_accessibility"
    const val RESULT_FAILED = "failed"
}
