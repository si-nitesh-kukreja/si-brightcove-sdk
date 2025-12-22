# Brightcove Live Streaming SDK for Android

A native Android SDK built with Jetpack Compose that provides a single, embeddable live streaming screen powered by Brightcove.

## Features

- ✅ **Native Jetpack Compose UI** - Fully native Compose implementation
- ✅ **Single Public Screen** - One composable (`LiveStreamScreen`) for easy integration
- ✅ **Multiple States** - Loading, Pre-live, and Live stream states with automatic transitions
- ✅ **Brightcove Integration** - Fully encapsulated Brightcove player logic with event handling
- ✅ **Analytics** - Built-in pageview tracking
- ✅ **Fullscreen Support** - Native fullscreen with system bar management and orientation handling
- ✅ **Error Handling** - Robust error recovery with retry mechanisms
- ✅ **AAR Distribution** - Packaged as Android Library (AAR) with Maven publishing
- ✅ **Demo App** - Included demo module for testing and validation

## Requirements

- Android API 24+ (Android 7.0+)
- Jetpack Compose
- Brightcove account (credentials are mapped internally based on event type and environment)

## Installation

### Gradle

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.si.brightcove:sdk:1.0.0")
}
```

Or if using a local AAR:

```kotlin
dependencies {
    implementation(files("libs/brightcove-live-stream-sdk.aar"))
}
```

### Maven Publishing

The SDK can be published to a Maven repository for distribution.

#### Prerequisites
- Ensure the `maven-publish` plugin is applied in your `build.gradle.kts` (already configured)
- Configure publishing settings in `gradle.properties`

#### 1. Configure Credentials

Add your Maven repository credentials to `gradle.properties`:

```properties
mavenUser=your_maven_username
mavenPassword=your_maven_password
```

#### 2. Publishing Configuration

The publishing block in `app/build.gradle.kts` should be configured as follows:

```kotlin
plugins {
    // ... other plugins
    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("mavenAar") {
            groupId = "com.si.brightcove"
            artifactId = "sdk"
            version = "1.0.0"

            from(components["release"])
        }
    }

    repositories {
        maven {
            name = "YourRepo"
            url = uri("https://your-maven-repo.com/releases")
            credentials {
                username = mavenUser
                password = mavenPassword
            }
        }
    }
}
```

#### 3. Publish Commands

**Publish to configured repository:**
```bash
./gradlew publish
```

**Publish to local Maven repository (for testing):**
```bash
./gradlew publishToMavenLocal
```

**Publish specific publication:**
```bash
./gradlew publishMavenAarPublicationToYourRepoRepository
```

#### 4. Verify Publication

After publishing, verify the artifact is available:

- **Local repo**: Check `~/.m2/repository/com/si/brightcove/sdk/1.0.0/`
- **Remote repo**: Check your Maven repository manager (e.g., Nexus, Artifactory)

#### 5. Using Published Artifact

Once published, other projects can use:

```kotlin
dependencies {
    implementation("com.si.brightcove:sdk:1.0.0")
}
```

## Quick Start

### 1. Initialize the SDK and Use LiveStreamScreen

Initialize the SDK and use `LiveStreamScreen` in your Compose UI. The SDK maps credentials internally based on `eventType` and `environment`, with optional overrides.

#### Basic Usage with Auto Initializing

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.si.brightcove.sdk.BrightcoveLiveStreamSDK
import com.si.brightcove.sdk.ui.LiveStreamScreen
import com.si.brightcove.sdk.model.EventType
import com.si.brightcove.sdk.model.Environment

@Composable
fun MyScreen() {
    // Use LiveStreamScreen
    LiveStreamScreen(
        eventType = EventType.MOBILE,
        environment = Environment.PROD,
        modifier = Modifier.fillMaxSize()
        // ... other parameters
    )
}
```

#### Advanced Usage (with Overrides and Configuration)

For more control, override credentials or adjust behavior:

```kotlin
@Composable
fun MyScreen() {
    // Initialize SDK with overrides
    BrightcoveLiveStreamSDK.initialize(
        context = LocalContext.current,
        eventType = EventType.MOBILE,
        environment = Environment.PROD,
        accountId = "CUSTOM_ACCOUNT_ID",     // Optional: Override default mapping
        policyKey = "CUSTOM_POLICY_KEY",     // Optional: Override default mapping
        videoId = "CUSTOM_VIDEO_ID",         // Optional: Override default mapping
        debug = BuildConfig.DEBUG,
        autoRetryOnError = true,             // Automatically retry on errors
        maxRetryAttempts = 3,                // Maximum retry attempts
        retryBackoffMultiplier = 2.0         // Exponential backoff multiplier
    )

    // Use LiveStreamScreen
    LiveStreamScreen(
        eventType = EventType.MOBILE,
        environment = Environment.PROD,
        modifier = Modifier.fillMaxSize()
        // ... other parameters
    )
}
```

Add the `LiveStreamScreen` composable to your existing Compose UI. The event type and environment are required and should match your initialization.

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.si.brightcove.sdk.ui.LiveStreamScreen
import com.si.brightcove.sdk.model.LiveStreamState
import com.si.brightcove.sdk.model.PlayerEvent
import com.si.brightcove.sdk.model.SDKError
import com.si.brightcove.sdk.model.EventType
import com.si.brightcove.sdk.model.Environment
import java.util.Date

@Composable
fun MyScreen() {
    LiveStreamScreen(
        eventType = EventType.MOBILE,
        environment = Environment.PROD,
        onClose = {
            // Handle close/dismiss action
            // e.g., navigate back, close dialog, etc.
        },
        onStateChanged = { state ->
            // Optional: React to state changes
            when (state) {
                is LiveStreamState.Loading -> {
                    println("Loading stream...")
                }
                is LiveStreamState.PreLive -> {
                    println("Stream not started yet. Scheduled: ${state.scheduledTime}")
                }
                is LiveStreamState.Live -> {
                    println("Stream is now live: ${state.title}")
                }
                is LiveStreamState.Error -> {
                    println("Error: ${state.errorMessage} (Code: ${state.errorCode})")
                }
            }
        },
        onError = { errorMessage, errorCode ->
            // Optional: Handle errors
            when (errorCode) {
                SDKError.NETWORK_ERROR -> {
                    // Show network error UI
                }
                SDKError.VIDEO_NOT_FOUND -> {
                    // Handle video not found
                }
                SDKError.PLAYBACK_ERROR -> {
                    // Handle playback errors
                }
                else -> {
                    // Handle other errors
                }
            }
        },
        onPlayerEvent = { event ->
            // Optional: Handle player events
            when (event) {
                is PlayerEvent.PlaybackStarted -> {
                    println("Playback started")
                }
                is PlayerEvent.Buffering -> {
                    println("Buffering...")
                }
                is PlayerEvent.PlaybackError -> {
                    println("Playback error: ${event.errorMessage}")
                }
                else -> {
                    // Handle other events
                }
            }
        },
        preLiveImageUrl = "https://example.com/preview-image.jpg",
        preLiveScheduledTime = Date(), // Scheduled start time
        liveTitle = "Live Stream Title",
        liveDescription = "Live stream description",
        showCloseButton = true,
        showPlayerControls = true,     // Show/hide native Brightcove controls
        errorRetryText = "Retry",      // Custom retry button text
        loadingText = "Loading...",    // Custom loading text
        modifier = Modifier.fillMaxSize()
    )
}
```

## API Reference

### BrightcoveLiveStreamSDK

#### `initialize()` (Basic)

Initialize the SDK with required event type and environment. Credentials are mapped internally.

**Parameters:**
- `context: Context` - Application context
- `eventType: EventType` - MOBILE or CAMERA (determines credential mapping)
- `environment: Environment` - PROD or NON_PROD (determines credential mapping)
- `debug: Boolean` - Enable debug logging (default: `false`)

**Throws:** `IllegalStateException` if SDK is already initialized

#### `initialize()` (Advanced)

Initialize the SDK with optional credential overrides and advanced configuration.

**Parameters:**
- `context: Context` - Application context
- `eventType: EventType` - MOBILE or CAMERA (determines credential mapping)
- `environment: Environment` - PROD or NON_PROD (determines credential mapping)
- `accountId: String?` - Optional override for Brightcove Account ID
- `policyKey: String?` - Optional override for Brightcove Policy Key
- `videoId: String?` - Optional override for Brightcove Video ID
- `debug: Boolean` - Enable debug logging (default: `false`)
- `autoRetryOnError: Boolean` - Whether to automatically retry on errors (default: `true`)
- `maxRetryAttempts: Int` - Maximum number of retry attempts (default: `3`)
- `retryBackoffMultiplier: Double` - Multiplier for exponential backoff (default: `2.0`)

**Note:** If accountId, policyKey, or videoId are not provided, the SDK uses hardcoded mappings based on eventType and environment.

### LiveStreamScreen

The main public composable for displaying live streams.

**Parameters:**
- `eventType: EventType` - MOBILE or CAMERA (required, determines credentials)
- `environment: Environment` - PROD or NON_PROD (required, determines credentials)
- `onClose: (() -> Unit)?` - Optional callback when user taps close/back button
- `onStateChanged: ((LiveStreamState) -> Unit)?` - Optional callback when stream state changes
- `onError: ((String, SDKError) -> Unit)?` - Optional callback when an error occurs
- `onPlayerEvent: ((PlayerEvent) -> Unit)?` - Optional callback for player events (playback, buffering, etc.)
- `preLiveImageUrl: String?` - URL for preview image shown before stream starts
- `preLiveScheduledTime: Date?` - Date/time when live stream is scheduled to start
- `liveTitle: String` - Title shown during live stream (default: `""`)
- `liveDescription: String` - Description shown during live stream (default: `""`)
- `showCloseButton: Boolean` - Whether to show close/back button (default: `true`)
- `showPlayerControls: Boolean` - Whether to show native Brightcove player controls (default: `true`)
- `errorRetryText: String` - Custom text for retry button in error state (default: `"Retry"`)
- `loadingText: String` - Custom text for loading indicator (default: `"Loading..."`)
- `modifier: Modifier` - Modifier for the composable (default: `Modifier`)

## States

The SDK exposes multiple states via `LiveStreamState` sealed class that parent apps can observe:

### LiveStreamState

A sealed class representing the current state of the live stream:

```kotlin
sealed class LiveStreamState {
    object Loading : LiveStreamState()
    
    data class PreLive(
        val imageUrl: String,
        val scheduledTime: Date
    ) : LiveStreamState()
    
    data class Live(
        val title: String,
        val description: String
    ) : LiveStreamState()
    
    data class Error(
        val errorMessage: String,
        val errorCode: SDKError,
        val retryable: Boolean = true
    ) : LiveStreamState()
}
```

### SDKError

Error codes for different types of errors:

```kotlin
enum class SDKError {
    NETWORK_ERROR,
    VIDEO_NOT_FOUND,
    PLAYBACK_ERROR,
    INITIALIZATION_ERROR,
    UNKNOWN_ERROR
}
```

### PlayerEvent

Events emitted by the player:

```kotlin
sealed class PlayerEvent {
    object PlaybackStarted : PlayerEvent()
    object PlaybackPaused : PlayerEvent()
    object PlaybackResumed : PlayerEvent()
    object Buffering : PlayerEvent()
    object BufferingComplete : PlayerEvent()
    data class PlaybackError(
        val errorMessage: String,
        val errorCode: SDKError
    ) : PlayerEvent()
    object VideoLoaded : PlayerEvent()
    object PlaybackCompleted : PlayerEvent()
}
```

### Loading State (`LiveStreamState.Loading`)

Displayed while checking stream availability:
- Shows live title, description, and a small loader
- Uses a black background to prevent white patches
- Automatically transitions to PreLive or Live state
- Parent apps can react to this state via `onStateChanged` callback

### Pre-Live State (`LiveStreamState.PreLive`)

Displayed before the live stream starts:
- Shows preview image (`imageUrl`)
- Displays scheduled start date/time (`scheduledTime`)
- Static screen with no video playback
- Triggers "pre_live_screen" pageview analytics
- Parent apps can react to this state via `onStateChanged` callback

### Live State (`LiveStreamState.Live`)

Displayed when live stream is active:
- Brightcove live video player with native controls (can be hidden)
- Title (`title`) and description (`description`) overlay (shown/hidden on tap)
- Header with close/back button (if enabled)
- Triggers "live_stream_screen" pageview analytics
- Parent apps can react to this state via `onStateChanged` callback

### Error State (`LiveStreamState.Error`)

Displayed when an error occurs:
- Shows error message (`errorMessage`)
- Displays error code (`errorCode`) for programmatic handling
- Shows retry button if `retryable` is `true`
- Automatically retries if `autoRetryOnError` is enabled in SDK config
- Parent apps can react to this state via `onStateChanged` and `onError` callbacks

### Using State Callbacks

You can optionally handle state changes in your app:

```kotlin
LiveStreamScreen(
    onStateChanged = { state ->
        when (state) {
            is LiveStreamState.PreLive -> {
                // Handle pre-live state
                // e.g., show notification, update UI, log analytics
                println("Stream scheduled for: ${state.scheduledTime}")
            }
            is LiveStreamState.Live -> {
                // Handle live state
                // e.g., enable features, track engagement
                println("Stream is live: ${state.title}")
            }
        }
    },
    // ... other parameters
)
```

## Features

### Error Handling & State Management
- ✅ **Loading State**: Shows loading indicator while checking stream availability
- ✅ **Error State**: Displays errors with retry capability
- ✅ **Automatic Retry**: Configurable auto-retry with exponential backoff
- ✅ **Manual Retry**: Exposed retry function for manual error recovery
- ✅ **Network Monitoring**: Automatic detection of network connectivity

### Player Controls & Customization
- ✅ **Player Events**: Callbacks for playback, buffering, errors, etc.
- ✅ **Control Visibility**: Show/hide native Brightcove player controls
- ✅ **Fullscreen Support**: Native fullscreen with system bar management and orientation handling
- ✅ **Event Tracking**: Track all player events for analytics

### Configuration & Customization
- ✅ **Polling Interval**: Configurable interval for checking stream availability
- ✅ **Retry Configuration**: Customizable retry attempts and backoff
- ✅ **UI Customization**: Custom loading text, error retry text
- ✅ **State Callbacks**: React to all state changes

### Network & Performance
- ✅ **Network State Handling**: Automatic pause/resume based on connectivity
- ✅ **Smart Polling**: Only polls when network is available
- ✅ **Error Recovery**: Automatic retry on network restoration

### Developer Experience
- ✅ **Enhanced Analytics**: Track all player events
- ✅ **Error Codes**: Type-safe error handling with SDKError enum
- ✅ **Comprehensive Callbacks**: onStateChanged, onError, onPlayerEvent
- ✅ **Better Error Messages**: Descriptive error messages with context

## Architecture

The SDK is designed with the following principles:

- **Encapsulation**: All Brightcove logic is internal to the SDK
- **No Dependencies**: Parent apps don't need to manage Brightcove or streaming logic
- **Lifecycle Management**: SDK handles player lifecycle automatically
- **Error Handling**: Graceful handling of network issues and playback failures with automatic retry
- **Network Awareness**: Automatic detection and handling of network state changes
- **Event-Driven**: Comprehensive event system for player and state changes

## Package Structure

```
com.si.brightcove.sdk
├── BrightcoveLiveStreamSDK          # Main initialization API
├── ui
│   └── LiveStreamScreen             # Public composable
├── viewmodel
│   ├── LiveStreamViewModel          # State management
│   └── LiveStreamViewModelFactory   # ViewModel factory
├── player
│   └── BrightcovePlayerView         # Brightcove player wrapper
├── model
│   ├── LiveStreamState              # State models
│   ├── EventType                    # MOBILE or CAMERA
│   └── Environment                  # PROD or NON_PROD
├── config
│   └── SDKConfig                    # Internal configuration
└── analytics
    └── AnalyticsManager              # Analytics tracking
```

## Demo App

The project includes a demo module (`demo/`) for testing and validation:

- **Purpose**: Test SDK integration without external dependencies
- **Setup**: Initializes the SDK and hosts `LiveStreamScreen`
- **Usage**: Run the demo app to verify functionality
- **Build**: `./gradlew :demo:assembleDebug`

Use the demo app to validate changes and ensure the SDK works as expected in a real Android application.

## Building the AAR

To build the SDK as an AAR:

```bash
./gradlew assembleRelease
```

The AAR will be generated at: `app/build/outputs/aar/app-release.aar`

## License

[Add your license here]

## Support

[Add support information here]

