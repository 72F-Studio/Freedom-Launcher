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

Add a code here before using it, and never reuse one for a different stage.
