package com.maxrave.simpmusic.ui.screen.other

import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.maxrave.domain.extension.now
import com.maxrave.simpmusic.expect.openUrl
import com.maxrave.simpmusic.expect.ui.DesktopMiniPlayerClearance
import com.maxrave.simpmusic.expect.ui.VerticalScrollBar
import com.maxrave.simpmusic.extension.ultraThinBarStyle
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.BuildKonfig
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.utils.enhancedAboutDetails
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    paddingValues: PaddingValues,
    navController: NavController,
) {
    val enhancedAbout = enhancedAboutDetails(
        buildChannel = BuildKonfig.enhancedBuildChannel,
        buildCode = BuildKonfig.versionCode,
        baseVersion = VersionManager.getVersionName(),
        commit = BuildKonfig.enhancedBuildCommit,
    )
    val hazeState = rememberHazeState()
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 64.dp)
                .verticalScroll(scrollState)
                .hazeSource(state = hazeState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        // App icon
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = "App Icon",
            modifier =
                Modifier
                    .size(150.dp)
                    .clip(CircleShape),
        )

        Spacer(modifier = Modifier.height(30.dp))

        // App name
        Text(
            text = enhancedAbout?.title ?: stringResource(Res.string.app_name),
            style = typo().titleLarge,
            fontSize = 22.sp,
        )

        // Fork version is NOT the same as the original SimpMusic version.
        Text(
            text = enhancedAbout?.buildLabel ?: stringResource(Res.string.version_format, VersionManager.getVersionName()),
            style = typo().bodySmall,
            fontSize = 13.sp,
        )
        if (enhancedAbout != null) {
            Text(
                text = enhancedAbout.baseVersionLabel,
                style = typo().bodySmall,
                fontSize = 13.sp,
            )
            Text(
                text = enhancedAbout.revisionLabel,
                style = typo().bodySmall,
                fontSize = 12.sp,
            )
        }

        // Preserve original author's credit and link for both upstream and fork.
        Text(
            text = enhancedAbout?.originalDeveloperLabel ?: stringResource(Res.string.maxrave_dev),
            style = typo().bodyMedium,
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier.clickable {
                    openUrl("https://maxrave.dev")
                },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Explain the fork before the unchanged original project's description.
        if (enhancedAbout != null) {
            Text(
                text = "SimpMusic Enhanced is a community fork with Enhanced 2+1 shuffle, progressive recommendations and player improvements.",
                style = typo().bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp),
                textAlign = TextAlign.Start,
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                TextButton(
                    onClick = { openUrl("https://github.com/aenorbiko/SimpMusic/blob/main/CHANGELOG.md") },
                    modifier = Modifier.align(Alignment.Start).padding(horizontal = 25.dp),
                ) {
                    Text("What's new in Enhanced")
                }
                TextButton(
                    onClick = { openUrl("https://github.com/aenorbiko/SimpMusic") },
                    modifier = Modifier.align(Alignment.Start).padding(horizontal = 25.dp),
                ) {
                    Text("Enhanced source on GitHub")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "About the original SimpMusic",
                style = typo().titleMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp),
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // App description
        Text(
            text = stringResource(Res.string.credit_app),
            style = typo().bodyMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(10.dp))

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            // Website button
            TextButton(
                onClick = {
                    openUrl("https://simpmusic.org")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.website))
            }

            // Developer blog button
            TextButton(
                onClick = {
                    openUrl("https://www.simpmusic.org/blogs")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Column {
                    Text(text = stringResource(Res.string.developer_blog))
                    Text(
                        text = stringResource(Res.string.developer_blog_tagline),
                        style = typo().bodySmall,
                    )
                }
            }

            // GitHub button
            TextButton(
                onClick = {
                    openUrl("https://github.com/maxrave-dev/SimpMusic")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.github))
            }

            // Issue tracker button
            TextButton(
                onClick = {
                    openUrl("https://github.com/maxrave-dev/SimpMusic/issues")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.issue_tracker))
            }

            // Buy me a coffee button
            TextButton(
                onClick = {
                    openUrl("https://github.com/sponsors/maxrave-dev")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.buy_me_a_coffee))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // README's "Legal Disclaimer & Terms of Use", bundled as files/legal_disclaimer.md: a copy,
        // so the two are updated together.
        val legalDisclaimer by produceState<String?>(null) {
            value = Res.readBytes("files/legal_disclaimer.md").decodeToString()
        }
        legalDisclaimer?.let {
            Markdown(
                it,
                typography =
                    markdownTypography(
                        h2 = typo().titleMedium,
                        h3 = typo().labelSmall,
                        text = typo().bodyMedium,
                        paragraph = typo().bodyMedium,
                        bullet = typo().bodyMedium,
                        textLink = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 25.dp, vertical = 10.dp),
            )
        }

        // Copyright text
        Text(
            text = stringResource(Res.string.copyright, now().year.toString()),
            style = typo().bodySmall,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp, vertical = 5.dp),
            textAlign = TextAlign.Start,
        )

        // Bottom spacing
        Spacer(modifier = Modifier.height(200.dp))
    }
    VerticalScrollBar(
        scrollState = scrollState,
        modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 64.dp, bottom = DesktopMiniPlayerClearance),
    )
    TopAppBar(
        modifier =
            Modifier
                .hazeBlur(HazeInput.Sources(hazeState), ultraThinBarStyle()),
        title = {
            Text(
                text = enhancedAbout?.title ?: stringResource(Res.string.app_name),
                style = typo().titleMedium,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(
                            align = Alignment.CenterVertically,
                        ).basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
        },
        navigationIcon = {
            Box(Modifier.padding(horizontal = 5.dp)) {
                RippleIconButton(
                    SimpIcons.ArrowBackIosNew,
                    Modifier
                        .size(32.dp),
                    true,
                    tint = MaterialTheme.colorScheme.onSurface,
                ) {
                    navController.navigateUp()
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                Color.Transparent,
                Color.Unspecified,
                Color.Unspecified,
                Color.Unspecified,
                Color.Unspecified,
            ),
    )
}