# Error codes

Every failure Eigen shows has its own stable code, defined in `ui/Problems.kt`. The problem sheet shows a headline and what to try; View more shows why it happened, the code and a redacted report, with a button that opens the person's own mail app with that report. The report never holds names, numbers, message text, file names or tokens (`kelvinRedact`).

| Code | Stage | What happened |
| --- | --- | --- |
| EIG-GES-01 | gesture:system | Reserved: a system gesture needed the gesture service (today this opens the disclosure sheet instead of an error). |
| EIG-GES-02 | gesture:global-action | The gesture service is on but Android refused the global action, or the service wasn't connected. |
| EIG-GES-03 | gesture:torch | No flash unit, or another app holds the camera. Also said inline by the assistant. |
| EIG-GES-04 | gesture:camera, gesture:app | No camera app answered, or the app a gesture opens is gone. |
| EIG-SRC-01 | search:contacts | The contacts provider failed to answer a search. |
| EIG-SRC-02 | search:start-shortcut | Android refused to start a shortcut from search. |
| EIG-SRC-03 | search:handoff | No app handles a web, Maps, Play, contact or settings handoff. |
| EIG-ICN-01 | icons:open-pack | An icon pack couldn't be read while picking an icon. |
| EIG-ICN-02 | icons:no-pack | Change icon was chosen with no icon pack installed. |
| EIG-PRV-01 | private:no-lock | Private apps need a secure screen lock and the phone has none. |
| EIG-PRV-02 | private:prompt, private:authenticate | The biometric prompt failed to start, or ended in an error (lockout, hardware). |
| EIG-AUT-01 | automation:tasker-missing, automation:tasker-run | A context's Tasker task couldn't run: Tasker missing, or delivery refused. |
| EIG-AUT-02 | automation:command | An automation app sent a command naming a context or colour Eigen doesn't have. |
| EIG-SET-01 | setup:export | Writing a .eigen file failed. |
| EIG-SET-02 | setup:open | The picked file couldn't be opened. |
| EIG-SET-03 | setup:validate, setup:apply | The file isn't a valid setup, or saving it failed (nothing is half-applied). |
| EIG-SET-04 | setup:validate | The setup was written by a newer Eigen. |
| EIG-AST-01 | assistant action | No app handles a timer, alarm, music, maps, call or search request. Said inline in the chat. |
| EIG-AST-02 | assistant summary | Reserved for summary failures; today an empty timeline answers in words. |
| EIG-TEX-01 | texture:living | Redrawing the living texture failed. |
| EIG-AI-01 | model:download | Downloading a Gemma model failed or was cut off (network, server). What arrived is kept and the next try resumes. |
| EIG-AI-02 | model:space | Not enough free storage for the chosen model. |
| EIG-AI-03 | model:verify | The downloaded file didn't match its SHA-256, so it was deleted. |
| EIG-AI-04 | model:terms | A download was started before the Gemma terms were accepted. |
| EIG-BAK-01 | backup:export | Writing the backup file failed (folder unavailable, no space). |
| EIG-BAK-02 | backup:open | The picked backup couldn't be opened or unpacked. |
| EIG-BAK-03 | backup:validate | The file isn't an Eigen backup, is damaged, or holds a value outside what Eigen allows. The report names the setting, never its value. |
| EIG-BAK-04 | backup:apply | Saving the restored settings failed; the previous settings were put back. |
| EIG-WAL-01 | wallpaper:open | The chosen photo couldn't be read, is over 32 MB, or isn't an image Android can decode. |
| EIG-WAL-02 | wallpaper:save | Eigen couldn't store its copy of the photo or texture. |
| EIG-WAL-03 | wallpaper:system | Android refused to set the photo as the phone's own wallpaper. |
| EIG-WAL-04 | wallpaper:picker | No app on the phone can pick an image. |
| EIG-LOK-01 | look:apply | A colour, look or icon pack change couldn't be applied; the previous look stays. |
| EIG-BIL-01 | billing:connect | Google Play Billing wasn't connected when a purchase or tip was tapped. |
| EIG-BIL-02 | billing:details | Play hadn't sent the product's details (price) yet, so no purchase could start. |
| EIG-BIL-03 | billing:flow | Play ended the purchase with an error (not a cancel); the report carries Play's response code. |
| EIG-ABT-01 | about:notices | The open-source notices bundled in the app couldn't be read. |
| EIG-FDB-01 | feedback:mail | Send feedback found no mail app. |
| EIG-AI-05 | model:run | The model wouldn't load or answer; the report carries the 72F AI Share code (AIS-01 to AIS-10). |
| EIG-APP-01 | app:launch | Android refused to start an app: its work profile is paused, or it was just updated or removed. |
| EIG-APP-02 | app:info | The app details page wouldn't open for the app or its profile. |
| EIG-APP-03 | app:shortcut | An app shortcut from the long-press menu wouldn't start; the app may have changed or turned it off. |
| EIG-APP-04 | app:handoff | No app handles an action Eigen hands to Android (uninstall, a settings page). |
| EIG-WID-01 | widgets:bind | The phone has no screen that lets Eigen ask for permission to show a widget. |
| EIG-WID-02 | widgets:configure | A widget's own settings screen wouldn't start. |
| EIG-WID-03 | widgets:place | A widget came back from its setup but its context was gone or already full. |

Add a code here before using it, and never reuse one for a different stage.
