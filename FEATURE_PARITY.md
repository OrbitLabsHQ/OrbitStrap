# Android feature comparison and compatibility

OrbitStrap independently implements comparable Android companion workflows.
It does not copy Voidstrap source code, graphics, names, or bundled assets.

| Android companion area | OrbitStrap status | Notes |
|---|---|---|
| Home/dashboard | Included | Roblox package status, launch shortcuts, configuration overview |
| Global/VN app detection | Included | Detects installed package variants |
| Launch Roblox | Included | Uses Android package launch intents |
| FastFlag JSON editor | Included | Local JSON editing; does not imply Roblox consumes it |
| Per-flag add/edit/delete | Included | Primitive JSON types are validated |
| Flag search/favorites | Included | Local metadata |
| Presets | Included | Built-in and user-created presets |
| Backup/restore | Included | Configuration, presets, favorites and history |
| Import/export | Included | Android Storage Access Framework |
| History/restore points | Included | Local snapshots, restore and compare |
| Game/deep links | Included | Validates accepted Roblox link formats before handing off |
| Device diagnostics | Included | Android/device/display/battery/thermal/RAM snapshots |
| Network diagnostics | Included | Web endpoint reachability; not live in-game latency |
| Themes | Included | Dark/light and accent colors |
| Update controls | Not included | Official app updates remain controlled by Android/Roblox |
| Root-only mod installation | Not included | Requires privileged access and compatible mod handling |
| Arbitrary client file replacement | Not included | Not supported by ordinary Android app sandbox |
| Client patching/injection | Not included | Deliberately excluded |
| Allowlist/security bypass | Not included | Deliberately excluded |
| In-game overlay/FPS hook | Not included | Requires supported game/system integration |
| Multi-instance launch | Not included | Not presented as a supported capability |
| Private game process/memory inspection | Not included | Not available to a normal app |

## Why some features are intentionally absent

Android isolates each app's private data. A normal app cannot freely edit another app's private files,
inspect its memory, inject code, or alter its network traffic. Shizuku is not a universal sandbox bypass.
A feature is only marked as included when the app has a real implementation using supported APIs.
