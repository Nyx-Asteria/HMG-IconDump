# Firmware dumper update — 1.0.0-rc.4

This update replaces the previous collection list with an incremental navigation-only capture. The original icon dumper on main is unchanged.

## Install and collect
1. Retain all previous captures and the known-good package. Obtain the signed RC4 build from the k5-firmware-dump branch.
2. Update with the same author key; if the signer differs, uninstall the old collector and apply removal before installing RC4.
3. Insert writable USB with sufficient free space (allow at least 250 MB). Apply the mod and restart if necessary.
4. Collection starts automatically when USB is available. To repeat it, toggle Dump firmware now OFF then ON.
5. Wait for FINISHED in inventory.txt; keep the new capture folder intact.

## New targets
- Exact stock navigation engine path /data/data/com.mnsoft.navi/BIN/libExSLNavi.so and system library alternative.
- Candidate iAP2 server APK and ODEX files, including case/vendor naming variants in approved system app directories.

Previous radio, media, DiPO, framework, audio and overlay files are no longer collected. The collector does not traverse private app data. The navigation binary is the sole exact-path exception.

## Verify and share
Output is under HMGAPI/K5FirmwareDump/capture-* (or the registered USB folder reported by the service). Include inventory.txt and firmware files. Check for missing/inaccessible paths and failed copies; FINISHED alone does not prove every target was found. System UID/SELinux may prevent engine access. Candidate server filenames are not vehicle-verified. Keep OEM binaries private. Remove the collector after use.

RC4 source is committed but build and vehicle validation remain pending.
