package example.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import example.feature.FeatureContent

@Composable
fun AppContent(modifier: Modifier = Modifier) {
    FeatureContent(modifier)
}
