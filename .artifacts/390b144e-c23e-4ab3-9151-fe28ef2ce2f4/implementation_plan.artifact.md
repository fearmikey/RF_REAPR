# Magnetometer Module Implementation Plan

Implement a fully functional Magnetometer module to detect magnetic field strength and direction, useful for identifying hidden electronics, wiring, or magnetic interference.

## Proposed Changes

### Data & Domain Layer

#### [NEW] [MagnetometerData.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/domain/model/MagnetometerData.kt)
Create a data class to hold X, Y, Z components and the calculated total strength ($\mu T$).

#### [NEW] [MagnetometerRepository.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/domain/repository/MagnetometerRepository.kt)
Define an interface for magnetic field data streaming.

#### [NEW] [MagnetometerRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/data/repository/MagnetometerRepositoryImpl.kt)
Implement the repository using Android's `SensorManager` and `TYPE_MAGNETIC_FIELD`.

---

### UI Layer

#### [NEW] [MagnetometerViewModel.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/physical/MagnetometerViewModel.kt)
Expose the magnetometer data flow as a `StateFlow`. Track peak values.

#### [MODIFY] [MagnetometerScreen.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/ui/physical/MagnetometerScreen.kt)
Replace the placeholder with a functional UI including:
- **Strength Gauge**: A visual representation of the total magnetic field.
- **Axis Breakdown**: Individual X, Y, Z readings.
- **Peak Tracking**: Display the highest reading observed in the current session.
- **Threshold Alerts**: Visual warnings when a high field strength is detected (e.g., > 100 $\mu T$).

---

### Integration

#### [MODIFY] [MainActivity.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/example/rf_reapr/MainActivity.kt)
- Instantiate `MagnetometerRepositoryImpl`.
- Update `MagnetometerScreen` route to provide the `MagnetometerViewModel`.

## Verification Plan

### Automated Tests
- Unit tests for `MagnetometerRepositoryImpl` (mocking `SensorManager` might be complex, but logic for total strength calculation can be tested).
- Unit tests for `MagnetometerViewModel` to ensure peak value is correctly updated.

### Manual Verification
- Deploy to a physical device (sensors are usually not available on emulators unless specially configured).
- Verify real-time updates of X, Y, Z values.
- Test "Peak" tracking.
- Test visual threshold alerts by bringing a magnet or electronic device close to the phone.
