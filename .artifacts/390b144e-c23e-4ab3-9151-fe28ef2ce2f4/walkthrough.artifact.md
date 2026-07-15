# Magnetometer Module Walkthrough

I have successfully implemented the Magnetometer module, transforming it from a placeholder into a functional tool for detecting magnetic fields.

## Key Changes

### Data & Domain Layer
- **[MagnetometerData.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/domain/model/MagnetometerData.kt)**: Encapsulates magnetic field vectors (X, Y, Z) and calculates total field strength in microteslas ($\mu T$).
- **[MagnetometerRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/domain/repository/MagnetometerRepository.kt)**: Interface for streaming magnetic sensor data.
- **[MagnetometerRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/data/repository/MagnetometerRepositoryImpl.kt)**: Uses Android's `SensorManager` and `TYPE_MAGNETIC_FIELD` to provide real-time updates.

### UI Layer
- **[MagnetometerViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/physical/MagnetometerViewModel.kt)**: Manages sensor lifecycle, tracks peak field strength, and exposes data to the UI.
- **[MagnetometerScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/physical/MagnetometerScreen.kt)**: A professional, tactical-style interface featuring:
    - **Dynamic Gauge**: Visualizes total strength with color-coded thresholds (Normal, Significant, High).
    - **Axis Breakdown**: Shows individual X, Y, and Z readings for precise orientation-based detection.
    - **Peak Tracking**: Records the highest reading encountered during the session.
    - **Status Indicators**: Clear text-based warnings when high fields are detected.

### Integration
- **[MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/MainActivity.kt)**: Wired the repository and ViewModel into the dependency graph and navigation host.

## Verification

### Manual Verification Required
> [!IMPORTANT]
> Magnetometer functionality requires physical hardware sensors. It is recommended to test on a physical device.
>
> 1.  Navigate to **Physical Access** -> **Magnetometer**.
> 2.  Observe ambient magnetic field (typically 25-65 $\mu T$).
> 3.  Move the device near a magnet, speaker, or laptop to see the gauge and readings increase.
> 4.  Verify that "Peak Observed" updates correctly.
> 5.  Test the **Reset** button (top right) to clear the peak value.

### Automated Checks
- The code has been checked for syntax errors and unused imports.
- Dependency injection follows the established "Manual DI" pattern in `MainActivity`.

## Technical Note
The module uses `SensorManager.SENSOR_DELAY_UI` to balance responsiveness with power efficiency. The sensor is automatically unregistered when navigating away from the screen to save battery.
