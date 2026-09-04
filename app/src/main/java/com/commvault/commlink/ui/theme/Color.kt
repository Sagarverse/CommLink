package com.commvault.commlink.ui.theme

import androidx.compose.ui.graphics.Color

// ── Ceramic Emerald Brand ───────────────────────────────────────────────
val PrimaryEmerald     = Color(0xFF039855)   // Main vibrant green
val SecondaryDark      = Color(0xFF0F172A)   // High contrast navy/black for text
val TertiaryMint       = Color(0xFF10B981)   // Lighter mint for secondary elements
val NeutralSlate       = Color(0xFF64748B)   // Neutral gray/slate for descriptions

// ── Light‑Mode Surface Palette ──────────────────────────────────────────
val PageBackground     = Color(0xFFF9FAFB)   // Soft off-white page background
val CardSurface        = Color(0xFFFFFFFF)   // Pure white for floating cards
val CardSurfaceAlt     = Color(0xFFF1F5F9)   // Alternate surface (section headers)
val BorderLight        = Color(0xFFF1F5F9)   // Very subtle border for cards

// ── Semantic Colors ─────────────────────────────────────────────────────
val SuccessTeal        = Color(0xFF00C49A)
val ErrorRed           = Color(0xFFEF4444)
val WarningAmber       = Color(0xFFF59E0B)
val InfoBlue           = Color(0xFF3B82F6)

// ── Gradients ───────────────────────────────────────────────────────────
val GradientBrandGreen = listOf(PrimaryEmerald, TertiaryMint)
val GradientNavyDeep   = listOf(SecondaryDark, Color(0xFF1E293B))

// ── Legacy aliases for compatibility ────────────────────────────────────
val BrandGreen         = PrimaryEmerald
val BrandMint          = TertiaryMint
val LightBg            = PageBackground
val LightSurface       = CardSurface
val LightSurfaceAlt    = CardSurfaceAlt
val BorderColor        = BorderLight
val TextPrimary        = SecondaryDark
val TextSecondary      = NeutralSlate
val TextTertiary       = Color(0xFF94A3B8)
val BrandGreenDark     = Color(0xFF027A48)
val BrandGreenSoft     = Color(0xFFD1FADF)
val BrandMintLight     = Color(0xFFA7F3D0)
val GlassWhite         = Color(0x33FFFFFF)
val GlassBorder        = Color(0x55FFFFFF)
val KeyBackground      = Color(0xFFEBEFF3)
val KeyPressed         = Color(0xFFD6DCE3)

val CommvaultNavy = SecondaryDark
val GradientTealMint = listOf(PrimaryEmerald, TertiaryMint)
val SuccessTealLight = Color(0xFFE6FFF7)
val GradientSunrise = listOf(WarningAmber, ErrorRed)

