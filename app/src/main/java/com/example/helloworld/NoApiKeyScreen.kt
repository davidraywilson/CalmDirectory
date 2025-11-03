package com.example.helloworld

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD

@Composable
fun NoApiKeyScreen(
    onSettingsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        append("You can obtain a key by creating credentials in the ")
        pushStringAnnotation(tag = "URL", annotation = "https://console.cloud.google.com/apis/credentials")
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
            append("Google Cloud Console")
        }
        pop()
        append(".")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "To keep this app free, you'll need to provide your own Google Places API key. " +
                    "This key is stored on your device and will not be shared with anyone.",
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            lineHeight = 26.sp
        )

        Spacer( modifier = Modifier.padding(16.dp))

        ButtonMMD(
            onClick = onSettingsClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Input API Key")
        }

        Spacer( modifier = Modifier.padding(16.dp))

        ClickableText(
            text = annotatedString,
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                    }
            },
            modifier = Modifier
                .padding(bottom = 16.dp),
            style = TextStyle(
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        )
    }
}
