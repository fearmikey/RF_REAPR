# Fix Centering of 2.4 GHz and 6 GHz Spectrum Graphs

The WiFi Spectrum Analyzer graphs for 2.4 GHz and 6 GHz currently appear uncentered. This is due to the frequency ranges used for the X-axis and the set of channel labels displayed.

## Proposed Changes

### [Component Name] UI WiFi

#### [MODIFY] [WifiChannelGraph.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/wifi/WifiChannelGraph.kt)

- **Adjust Frequency Ranges**:
    - **2.4 GHz**: Change range from `[2400, 2495]` to `[2400, 2484]`. This centers the common channels 1-13 (centered at 2442 MHz) perfectly.
    - **6 GHz**: Maintain range `[5925, 7125]` as it is already mathematically centered for the full band, but improve the labeling.
- **Update Channel Labels**:
    - **2.4 GHz**: Continue showing 1-13.
    - **6 GHz**: Add more labels to cover the upper part of the 1200 MHz wide band. Currently, it stops at 193. I will add labels up to 225 or 233.
    - **5 GHz**: (Bonus) Slightly adjust to `[5145, 5840]` to better center channels 36-161 if needed, though 2.4 and 6 are the priorities.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the WiFi Spectrum Analyzer.
- Toggle between 2.4 GHz and 6 GHz bands.
- Observe that the channel labels are distributed more evenly across the width of the graph.
- (Optional) Use simulated data (Import JSON) to verify APs appear in the correct positions relative to the new labels.
