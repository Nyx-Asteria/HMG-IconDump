# HMG K5 Firmware Dump

Standalone read-only firmware collector derived from HMG-IconDump. This branch does not install launcher hooks or change vehicle settings. Install the signed test package, insert USB, restart the head unit if needed and wait for the inventory FINISHED line. Output: `HMGAPI/K5FirmwareDump/capture-*/firmware/` and `inventory.txt`. Missing or inaccessible files are recorded; partial copies are deleted. Keep OEM files private. Uninstall after collection. Earlier versions targeted K5 radio, automotive framework, DiPO and audio HAL analysis; package uses system UID, so inaccessible files may require a separate authorized collection method. Main branch remains the original icon dumper.

## RC4 — incremental navigation capture

Collects only the stock `libExSLNavi.so` engine and candidate iAP2 server APK/ODEX files. Previous audio, radio, DiPO, framework and overlay collection is disabled; retain earlier archives. The exact `/data/data/com.mnsoft.navi/BIN/libExSLNavi.so` path is the only private-data exception; no directory traversal under `/data` is performed. System UID may be denied by filesystem permissions or SELinux; inventory records missing/inaccessible files. Candidate server package names are not yet vehicle-verified. Keep OEM binaries private.

See [RC4 update and collection instructions](UPDATE.md) before installing. The RC4 build and vehicle collection have not yet been verified.
