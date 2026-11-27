#!/usr/bin/env python3
"""Keep the Classic player radio button on the protected path and comments in its menu."""
from pathlib import Path

root = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui")
spotify = (root / "screen/player/content/NowPlayingContentSpotify.kt").read_text()
state = (root / "screen/player/content/NowPlayingContentState.kt").read_text()
screen = (root / "screen/player/NowPlayingScreen.kt").read_text()
menu = (root / "component/ModalBottomSheet.kt").read_text()

toolbar = spotify.split("// List Bottom Buttons", 1)[1].split("this@Column.AnimatedVisibility", 1)[0]
assert "onClick = actions.onStartRadio" in toolbar
assert toolbar.index("SimpIcons.Sensors") < toolbar.index("SimpIcons.PlaylistAdd") < toolbar.index("SimpIcons.QueueMusic")
assert "SimpIcons.CommentOutline" not in toolbar
assert "actions.onShowComments()" not in toolbar
assert "contentDescription = stringResource(Res.string.start_radio)" in toolbar
assert "val onStartRadio: () -> Unit" in state
assert "onStartRadio = { mediaPlayerHandler.toggleRadio() }" in screen
assert "onShowComments = actions.onShowComments" in screen
row = menu.split("if (onShowComments != null) {", 1)[1].split("icon = SimpIcons.Sensors,", 1)[0]
assert "SimpIcons.CommentOutline" in row and "Res.string.comments" in row
assert row.index("modelBottomSheetState.hide()") < row.index("onDismiss()") < row.index("onShowComments()")
print("PASS: Classic Radio goes directly to the protected handler; comments remain in the current-song menu.")

# New development builds share the installed Start Radio Lab identity and key.
gradle = Path("androidApp/build.gradle.kts").read_text()
workflow = Path(".github/workflows/enhanced-player-radio-debug.yml").read_text()
action = Path(".github/actions/enhanced-android-build/action.yml").read_text()
assert 'providers.gradleProperty("simpmusicSharedLabExperimental")' in gradle
assert 'isSharedLab -> ".dev.startradio"' in gradle
assert 'else -> ".dev"' in gradle
assert 'lab_property: simpmusicSharedLabExperimental' in workflow
assert 'expected_package: com.maxrave.simpmusic.dev.startradio' in workflow
assert 'node.text="SimpMusic Lab" if sys.argv[2]=="simpmusicSharedLabExperimental"' in action
assert 'import com.maxrave.simpmusic.ui.icon.Sensors' in spotify
assert 'import com.maxrave.simpmusic.ui.icon.CommentOutline' in menu
print("PASS: shared Lab uses the existing Start Radio package, not the Stable package.")
