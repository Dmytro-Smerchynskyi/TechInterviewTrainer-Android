package com.oniolla.interviewer.ui

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme

internal object LegalLinks {
    const val TERMS = "https://interviewtrainer.app/terms"
    const val PRIVACY = "https://interviewtrainer.app/privacy"
}

@Composable
internal fun rememberLegalUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(context, uriHandler) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.legal_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
internal fun AuthLegalNotice(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val notice = stringResource(R.string.auth_legal_notice)
    val terms = stringResource(R.string.legal_terms_link)
    val privacy = stringResource(R.string.legal_privacy_link)
    val openUrl = rememberLegalUrlOpener()
    val linkStyle = SpanStyle(color = AppTheme.colors.link, fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.None)
    val text = buildAnnotatedString {
        var offset = 0
        Regex("%([12])[$]s").findAll(notice).forEach { match ->
            append(notice.substring(offset, match.range.first))
            val isTerms = match.groupValues[1] == "1"
            val url = if (isTerms) LegalLinks.TERMS else LegalLinks.PRIVACY
            val start = length
            append(if (isTerms) terms else privacy)
            if (enabled) {
                addLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle)) {
                    openUrl(url)
                }, start, length)
            } else {
                addStyle(linkStyle, start, length)
            }
            offset = match.range.last + 1
        }
        append(notice.substring(offset))
    }
    Text(text, modifier, color = AppTheme.colors.secondaryText, fontSize = 10.sp,
        lineHeight = 16.sp, textAlign = TextAlign.Center)
}
