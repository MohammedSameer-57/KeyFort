# KeyFort Repository Cleanup Log

This log records every structural restructuring, file relocation, and deletion performed in accordance with Section B of the project brief.

| Date | Action | Source | Target | Reason | Test Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 2026-11-24 | Move | `THREAT_MODEL.md` | `docs/THREAT_MODEL.md` | Standardizing documentation hierarchy under `docs/`. | Green (100/100) |
| 2026-11-24 | Move | `design/` | `docs/design/` | Isolating reference UI assets under `docs/`. | Green (100/100) |
| 2026-11-24 | Delete | `KeyFort_Implementation_Plan.pdf` | - | Removing marketing/planning PDF per Section B2. | Green (100/100) |
| 2026-11-24 | Delete | `Dockerfile` | - | Removing container definition per Section B2. | Green (100/100) |
| 2026-11-24 | Delete | `.github/` | - | Removing issue templates per Section B2. | Green (100/100) |
| 2026-11-24 | Delete | `CONTRIBUTING.md` | - | Streamlining root tree per Section B1. | Green (100/100) |
| 2026-11-24 | Delete | `scripts/OPEN_DEMO.bat`, `scripts/SHARE_TO_PHONE.bat` | - | Superseded by standard `RUN_DEMO` launchers. | Green (100/100) |
| 2026-11-24 | Delete | `scripts/START_KEYFORT.bat`, `scripts/START_KEYFORT.ps1` | - | Superseded by `RUN_DEMO` launchers; zero external references. | Green (100/100) |
| 2026-11-24 | Delete | `preview.html` | - | Removing obsolete standalone simulation prototype per Section B2. | Green (100/100) |
