# Magnetometer Enhancements Walkthrough

I have updated the Magnetometer module with user guidance and improved stability for the detection status.

## Key Enhancements

### User Guidance
- **Accuracy Tip Popup**: A new `AlertDialog` appears when the Magnetometer screen is opened. It advises users: *"For the most accurate results, avoid covering the back of your phone with your hand, as this can interfere with the internal magnetometer sensor."*

### Stability & UX
- **Field Status Hysteresis**:
    - Introduced a `FieldStatus` enum (`WEAK`, `NORMAL`, `SIGNIFICANT`, `HIGH`).
    - Implemented hysteresis logic in the `MagnetometerViewModel` with a $5\mu T$ margin. This ensures that the detection banner doesn't flicker rapidly between statuses if the reading fluctuates slightly around a threshold.
    - The `DetectionStatus` UI component now consumes this stable state.

## Technical Details
- **Hysteresis Logic**: Transitions between states now require crossing a threshold plus a small buffer. For example, to move from `NORMAL` to `SIGNIFICANT` (threshold $80\mu T$), the reading must exceed $85\mu T$. To move back down, it must drop below $75\mu T$.

## Verification

### Manual Verification Required
1.  **Popup**: Open the Magnetometer module and verify the "Accuracy Tip" appears. Confirm it can be dismissed.
2.  **Hysteresis**: Use a magnetic source to bring the reading near a threshold (e.g., $80\mu T$). Observe the status banner and confirm it remains stable even if the numerical value fluctuates slightly.
