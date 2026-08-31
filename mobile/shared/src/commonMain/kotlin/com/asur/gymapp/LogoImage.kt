package com.asur.gymapp

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import liftlog.shared.generated.resources.Res
import liftlog.shared.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun LogoImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.logo),
        contentDescription = "RepUp logo",
        modifier = modifier
    )
}