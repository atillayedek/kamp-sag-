package com.kampusagi.android.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

val KampusAgiShapes = Shapes(
    small = RoundedCornerShape(KampusAgiSpacing.inputCornerRadius),
    medium = RoundedCornerShape(KampusAgiSpacing.buttonCornerRadius),
    large = RoundedCornerShape(KampusAgiSpacing.cardCornerRadius),
)
