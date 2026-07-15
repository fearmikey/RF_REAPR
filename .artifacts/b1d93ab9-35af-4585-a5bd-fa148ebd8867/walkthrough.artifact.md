# Walkthrough - Centering WiFi Spectrum Graphs

I have adjusted the frequency ranges and channel labels for the WiFi Spectrum Analyzer to ensure the graphs appear centered and provide better coverage across all bands (2.4 GHz, 5 GHz, and 6 GHz).

## Changes Made

### UI Adjustments in `WifiChannelGraph.kt`

- **2.4 GHz Band**:
    - Adjusted range to `[2400 MHz, 2484 MHz]`.
    - This perfectly centers the standard channels 1 through 13.
- **5 GHz Band**:
    - Adjusted range to `[5145 MHz, 5840 MHz]`.
    - This centers the common channel set (36 to 161) evenly within the view.
- **6 GHz Band**:
    - Maintained the `[5925 MHz, 7125 MHz]` range but improved labeling.
    - Added channel **233** (the upper bound of the band) to the labels to ensure they span the full width of the graph.

### ViewModel Adjustments in `WifiFingerprintViewModel.kt`

- Updated the `filteredAccessPoints` logic to match the new frequency ranges used in the UI. This ensures that any access point visible in the list will also be within the bounds of the graph.

## Verification Results

### Automated Tests
- Ran `:app:assembleDebug` to verify the project builds successfully with the changes.

### Manual Verification Recommendation
- Open the **WiFi Spectrum Analyzer**.
- Switch between the 2.4, 5, and 6 GHz tabs.
- Verify that the channel labels (numbers at the bottom of the graph) are distributed symmetrically relative to the edges of the graph.
