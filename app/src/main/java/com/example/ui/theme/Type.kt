package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSans = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
)

val JetBrainsMono = FontFamily(
  Font(R.font.jetbrains_mono, FontWeight.Normal),
  Font(R.font.jetbrains_mono, FontWeight.SemiBold),
)

// Strictly 3 type sizes (22.sp, 15.sp, 12.sp) and 2 weights (Normal, SemiBold)
private val Title22SemiBold =
  TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp,
  )

private val Body15Normal =
  TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.1.sp,
  )

private val Body15SemiBold =
  TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.1.sp,
  )

private val Mono12Normal =
  TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
  )

private val Mono12SemiBold =
  TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
  )

val Typography =
  Typography(
    headlineSmall = Title22SemiBold,
    titleLarge = Title22SemiBold,
    titleMedium = Body15SemiBold,
    titleSmall = Body15SemiBold,
    bodyLarge = Body15Normal,
    bodyMedium = Body15Normal,
    bodySmall = Mono12Normal,
    labelLarge = Body15SemiBold,
    labelMedium = Mono12SemiBold,
    labelSmall = Mono12Normal,
  )
