package com.jpcottin.shakedetectortest.ui.main

enum class ShakeLevel {
  NONE,
  SMALL,
  BIG,
}

const val SMALL_SHAKE_THRESHOLD = 11f
const val BIG_SHAKE_THRESHOLD = 16f

fun classifyShake(acceleration: Float): ShakeLevel =
  when {
    acceleration > BIG_SHAKE_THRESHOLD -> ShakeLevel.BIG
    acceleration > SMALL_SHAKE_THRESHOLD -> ShakeLevel.SMALL
    else -> ShakeLevel.NONE
  }
