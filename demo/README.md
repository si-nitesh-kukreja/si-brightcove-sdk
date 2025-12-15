# Brightcove Live Streaming SDK - Demo App

This is a demo/test application to verify that the Brightcove Live Streaming SDK is working correctly.

## Setup Instructions

1. **Update Brightcove Credentials**
   
   Open `DemoApplication.kt` and replace the placeholder values with your actual Brightcove credentials:
   
   ```kotlin
   BrightcoveLiveStreamSDK.initialize(
       context = this,
       accountId = "YOUR_ACCOUNT_ID",      // Replace with your Account ID
       policyKey = "YOUR_POLICY_KEY",       // Replace with your Policy Key
       videoId = "YOUR_VIDEO_ID",          // Replace with your Video ID
       debug = BuildConfig.DEBUG
   )
   ```

2. **Run the Demo App**
   
   - Select the `demo` run configuration in Android Studio
   - Run the app on an emulator or physical device
   - The app will initialize the SDK and display the LiveStreamScreen

## What the Demo App Tests

The demo app demonstrates and tests:

- ✅ SDK initialization
- ✅ Loading state display
- ✅ Pre-live state with preview image and scheduled time
- ✅ Live stream playback with Brightcove player
- ✅ Error state with retry functionality
- ✅ State change callbacks
- ✅ Error callbacks
- ✅ Player event callbacks (playback, buffering, etc.)
- ✅ Close button functionality
- ✅ Network state handling
- ✅ Automatic retry on errors

## Logging

The demo app logs all events to Logcat with the tag "DemoApp". You can filter by this tag to see:
- State changes
- Errors
- Player events

## Testing Different Scenarios

1. **Test Pre-Live State**: Use a video ID that hasn't started streaming yet
2. **Test Live State**: Use a video ID that is currently live
3. **Test Error State**: Use an invalid video ID or disable network
4. **Test Retry**: Trigger an error and verify retry functionality
5. **Test Network Handling**: Toggle airplane mode to test network state handling

## Notes

- Make sure you have valid Brightcove credentials before running
- The app requires internet connection to fetch video data
- All callbacks are logged to Logcat for debugging

