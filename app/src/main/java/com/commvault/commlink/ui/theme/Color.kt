package com.commvault.commlink.ui.theme

import androidx.compose.ui.graphics.Color

// ── Commvault Official Brand ────────────────────────────────────────────
val CommvaultNavy      = Color(0xFF0B2E44)   // Deep navy – used for text / headers
val CommvaultPink      = Color(0xFFFF4A6A)   // Signature vibrant pink accent
val CommvaultPinkDark  = Color(0xFFD63B56)   // Darker pink for pressed states
val CommvaultPinkSoft  = Color(0xFFFFE0E6)   // Soft blush for subtle backgrounds
val CommvaultPurple    = Color(0xFF8E24AA)   // Vibrant purple for gradients
val CommvaultPurpleLight = Color(0xFFCE93D8) // Light purple for secondary accents

// ── Light‑Mode Surface Palette ──────────────────────────────────────────
val LightBg            = Color(0xFFF5F6FA)   // Page‑level background (warm off‑white)
val LightSurface       = Color(0xFFFFFFFF)   // Card / panel surface
val LightSurfaceAlt    = Color(0xFFF0F2F5)   // Alternate surface (section headers)
val BorderColor        = Color(0xFFE2E8F0)   // Subtle card borders (warmer)

// ── Glassmorphism ───────────────────────────────────────────────────────
val GlassWhite         = Color(0x33FFFFFF)   // 20% white for glass panels
val GlassBorder        = Color(0x55FFFFFF)   // 33% white for glass borders

// ── Text Hierarchy ──────────────────────────────────────────────────────
val TextPrimary        = Color(0xFF0B2E44)   // Headings & body text (navy)
val TextSecondary      = Color(0xFF4A6A7D)   // Descriptions / secondary info
val TextTertiary       = Color(0xFF8DA4B5)   // Hints, labels, disabled text

// ── Semantic Colors ─────────────────────────────────────────────────────
val SuccessTeal        = Color(0xFF00C49A)   // Connected / success states
val SuccessTealLight   = Color(0xFFE0F7F1)   // Success background tint
val ErrorRed           = Color(0xFFFF5252)   // Error banners
val WarningAmber       = Color(0xFFFFB74D)   // Warning states
val InfoBlue           = Color(0xFF42A5F5)   // Info accents

// ── Premium Gradients (reusable brush definitions) ──────────────────────
val GradientPinkPurple = listOf(CommvaultPink, CommvaultPurple)
val GradientNavyDeep   = listOf(Color(0xFF0B2E44), Color(0xFF1A3F5C))
val GradientTealMint   = listOf(Color(0xFF00C49A), Color(0xFF00E5B0))
val GradientSunrise    = listOf(Color(0xFFFF6B6B), Color(0xFFFFD93D))

// ── Keyboard & Specific Components ──────────────────────────────────────
val KeyBackground      = Color(0xFFEBEFF3)   // Key cap background (light)
val KeyPressed         = Color(0xFFD6DCE3)   // Key cap pressed state

// ── Legacy aliases (mapped to new light values so existing screens compile) ─
val NavyBg             = LightBg
val NavySurface        = LightSurface
val NavySurfaceLight   = LightSurfaceAlt
