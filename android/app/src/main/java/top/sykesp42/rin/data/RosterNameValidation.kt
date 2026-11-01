package top.sykesp42.rin.data

/** Mirrors LocalSchoolApi's name rules before saving a one-tap roster name. */
fun rosterNameError(raw: String): String? {
    val name = raw.trim()
    if (name.isEmpty()) return "填写名单姓名"
    if (name.length !in 2..64) return "姓名需 2–64 个字符"
    if (name.any { it.code < 0x20 }) return "姓名包含不可用字符"
    return null
}
