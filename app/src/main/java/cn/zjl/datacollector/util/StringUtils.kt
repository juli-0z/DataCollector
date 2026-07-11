@file:JvmName("StringUtils")

package cn.zjl.datacollector.util

/**
 * 空安全字符串修剪。
 * 为 null 时返回空字符串，非 null 时去除首尾空白。
 */
fun safeText(value: String?): String = value?.trim() ?: ""