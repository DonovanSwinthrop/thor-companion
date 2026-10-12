package androidx.compose.ui.text.font

/** Android's asset-backed Font(path, assets[, weight, style]). */
fun Font(
    path: String,
    assetManager: android.content.AssetManager,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = androidx.compose.ui.text.platform.Font(path, assetManager.open(path).readBytes(), weight, style)
