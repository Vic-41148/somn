package dev.vic41148.somn.core.ui.theme

import androidx.compose.ui.graphics.Color

// Primary — Deep indigo for sleep/night association
val PrimaryLight = Color(0xFF4A5AC7)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDEE0FF)
val OnPrimaryContainerLight = Color(0xFF001258)

val PrimaryDark = Color(0xFFBBC3FF)
val OnPrimaryDark = Color(0xFF102484)
val PrimaryContainerDark = Color(0xFF2D40A0)
val OnPrimaryContainerDark = Color(0xFFDEE0FF)

// Secondary — Soft lavender for calm
val SecondaryLight = Color(0xFF5B5D72)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE0E0F9)
val OnSecondaryContainerLight = Color(0xFF181A2C)

val SecondaryDark = Color(0xFFC4C4DD)
val OnSecondaryDark = Color(0xFF2D2F42)
val SecondaryContainerDark = Color(0xFF434559)
val OnSecondaryContainerDark = Color(0xFFE0E0F9)

// Tertiary — Dusty mauve, a calm accent intended for secondary flavour
val TertiaryLight = Color(0xFF77536D)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD7F1)
val OnTertiaryContainerLight = Color(0xFF2D1228)

val TertiaryDark = Color(0xFFE6BAD7)
val OnTertiaryDark = Color(0xFF44263D)
val TertiaryContainerDark = Color(0xFF5D3C55)
val OnTertiaryContainerDark = Color(0xFFFFD7F1)

// Background & Surface
val BackgroundLight = Color(0xFFFEFBFF)
val OnBackgroundLight = Color(0xFF1B1B1F)
val SurfaceLight = Color(0xFFFEFBFF)
val OnSurfaceLight = Color(0xFF1B1B1F)

val BackgroundDark = Color(0xFF0E0E11)   // True OLED black option
val OnBackgroundDark = Color(0xFFE4E1E6)
val SurfaceDark = Color(0xFF1B1B1F)
val OnSurfaceDark = Color(0xFFE4E1E6)

// Surface, outline and error roles that complete the scheme. Without them the colour scheme
// falls back to Material's baseline purple for the roles screens actually use (cards, chips,
// text fields, outlines, disabled content), so non-dynamic devices leaked purple into a sea
// of indigo. Dark surfaces climb a monotonic tone ladder out of true black (4/8/10/12/17/22)
// so the container hierarchy resolves cleanly. Light ones descend from white the same way.
val SurfaceVariantLight = Color(0xFFE3E1EC)
val OnSurfaceVariantLight = Color(0xFF44464F)
val SurfaceDimLight = Color(0xFFDAD8DF)
val SurfaceBrightLight = Color(0xFFFEFBFF)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF7F7FB)
val SurfaceContainerLight = Color(0xFFF2F2F7)
val SurfaceContainerHighLight = Color(0xFFECECF1)
val SurfaceContainerHighestLight = Color(0xFFE6E6EC)
val OutlineLight = Color(0xFF74777F)
val OutlineVariantLight = Color(0xFFC4C6CE)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val InverseSurfaceLight = Color(0xFF333136)
val InverseOnSurfaceLight = Color(0xFFF1F0F4)
val InversePrimaryLight = Color(0xFFBBC3FF)
val ScrimLight = Color(0xFF000000)

val SurfaceVariantDark = Color(0xFF46464F)
val OnSurfaceVariantDark = Color(0xFFC4C4D0)
val SurfaceDimDark = Color(0xFF100F13)
val SurfaceBrightDark = Color(0xFF3C3C40)
val SurfaceContainerLowestDark = Color(0xFF0B0B0F)
val SurfaceContainerLowDark = Color(0xFF1E1E22)
val SurfaceContainerDark = Color(0xFF222227)
val SurfaceContainerHighDark = Color(0xFF2A2A2F)
val SurfaceContainerHighestDark = Color(0xFF333338)
val OutlineDark = Color(0xFF8E9099)
val OutlineVariantDark = Color(0xFF46464F)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)
val InverseSurfaceDark = Color(0xFFE4E1E6)
val InverseOnSurfaceDark = Color(0xFF343337)
val InversePrimaryDark = Color(0xFF4A5AC7)
val ScrimDark = Color(0xFF000000)

// Sleep Score colors — a fixed ramp independent of dynamic colour, so a score means the same
// green anywhere in the app. Green (well) to amber (caution) to red (poor). Prepared next to
// the Okabe-Ito palette below so the ramp has no glaring collision with stage/cycle hues.
val ScoreGreat = Color(0xFF43A047)
val ScoreGood = Color(0xFF9CCC65)
val ScoreFair = Color(0xFFFFC107)
val ScorePoor = Color(0xFFEF5350)

// Sleep stage colors — Okabe-Ito colour-blind-safe set (orange/blue/dark-blue/rose). The old
// 2014 Material swatches collided with the score ramp and the cycle bands (light blue and
// magenta appeared in both Stage and Cycle contexts), which made a shared legend ambiguous.
val StageAwake = Color(0xFFD55E00)
val StageLight = Color(0xFF56B4E9)
val StageDeep = Color(0xFF0072B2)
val StageRem = Color(0xFFCC79A7)

// Sleep debt severity — was duplicated inline as raw hex in both HomeScreen and
// SleepDebtDetailScreen. Named here so the two cannot drift out of sync with each other.
// Already an ordered amber-to-red ramp, so it stays as it is.
val DebtMild = Color(0xFFF9A825)
val DebtModerate = Color(0xFFE65100)

// Menstrual cycle phase bands (DATA-04, TrendsScreen) — kept at full alpha here; callers apply
// their own alpha since the same hue is used at different opacities depending on context.
// Paired from the densified Okabe-Ito / IBM fortified palette so every band is distinct even
// with the adjacent alpha-blended stage and score hues in the same chart.
val CycleMenstrual = Color(0xFFFE6100)
val CycleFollicular = Color(0xFF009E73)
val CycleOvulation = Color(0xFFF0E442)
val CycleLuteal = Color(0xFF648FFF)
val CyclePremenstrual = Color(0xFFE69F00)

// Audio event type colors (AudioTimeline) — IBM fortified palette, shifted away from the score
// ramp: the old amber/orange/red here were indistinguishable from ScoreFair/ScorePoor.
val AudioEventTalk = Color(0xFF785EF0)
val AudioEventSnore = Color(0xFFFFB000)
val AudioEventCough = Color(0xFFDC267F)
