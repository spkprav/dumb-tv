# TV privacy: what can and cannot be blocked

On TVs running Google TV, Google explicitly documents collection of identifiers, installed apps, app interactions, diagnostics and watch activity: [How Google TV handles your data](https://support.google.com/googletv/answer/13427970?hl=en). This does not establish that every possible event is collected on your TV.

Sony documents optional Samba Interactive TV viewing recognition on certain BRAVIA models and says disabling it stops that service collecting viewing history: [Sony: Information about Samba TV](https://www.sony.com/electronics/support/articles/00182856). That page’s model list does not establish that Samba is installed on a particular TV. Check your actual firmware/app list; do not assume its presence or infer that the absence of Samba means the absence of all telemetry.

## Controls to review

- In TV settings, turn off Usage & diagnostics where available. [Sony’s instructions](https://www.sony.co.uk/electronics/support/articles/00155797) describe this setting; menu placement varies with firmware.
- Review Privacy → Ads and advertising-ID personalization/deletion options that your version offers. These affect ad personalization, not all collection. [Google’s explanation](https://support.google.com/googletv/answer/13392198).
- Turn off unneeded location and app permissions. IP-based approximate location may still be available while online. [Google’s data explanation](https://support.google.com/googletv/answer/13427970?hl=en).
- Disable Samba Interactive TV if present and enabled; review Sony’s privacy choices on the TV. Revoking consent does not automatically erase previously collected data.

## In-app firewall

The launcher defaults to requesting an Android VpnService per-app deny tunnel for installed, explicitly named ACR/recommendation applications. It drops their routed traffic locally and lists actual selected targets. It does not add Jellyfin, Prime Video, Hotstar, or the launcher itself to the tunnel. Core Google Play services are not blocked because their network functions are shared with sign-in and other platform features. System UIDs and visibly shared identities are excluded rather than risking blocking unrelated applications.

This improves control over selected packages without a router change, but is not a universal firewall, cannot disable all privileged telemetry, does not stop local data collection, and cannot filter tracking selectively out of a permitted streaming app’s encrypted traffic. Android’s [VPN guide](https://developer.android.com/develop/connectivity/vpn) explains per-app routing, consent, the single active VPN, and the effect of blocking connections outside the VPN.

For stronger off-device network isolation, deny the TV WAN egress on both IPv4 and IPv6 while allowing only the needed local services (for example Jellyfin at your local Jellyfin server). That stops internet streaming, updates and external wallpaper downloads. It does not prevent the TV recording data locally or uploading it later if internet access is restored. This router-level policy has not been applied by the launcher.

The launcher disables Android app backup for its own data. Jellyfin credentials are not embedded in the APK, and its encrypted session is stored in the no-backup directory. Online wallpapers still contact their image host; select Fixed to avoid those downloads.

The app’s Settings privacy screen provides controls and this limitation notice; enabling it does not claim to remove all tracking or erase server-side histories.

## Default startup and turning protection off

The firewall is requested by default. On first Home launch Android presents its VPN approval UI; blocking is active only after approval and successful tunnel creation. Denying/cancelling approval, turning protection off in the app/notification, or Android revoking the VPN disables automatic startup. You can enable it again in Settings → General → Privacy & tracking. The UI reports the live tunnel state separately from the saved preference; it never reports protection merely because the default preference is on.

After approval, startup is attempted on Home resume, device boot, and app replacement. Android background-start restrictions or firmware can prevent startup; this has not been verified on this TV. Android Always-on VPN can manage reboot/restart persistence where the TV exposes that option. Keep “Block connections without VPN” off, because streaming and local-library apps intentionally bypass the deny tunnel. Force-stopping the app prevents its background restart until it is opened again. This is not a guarantee of uninterrupted blocking.

Sony’s documented Samba opt-out is System / System Preferences → Samba Interactive TV → Privacy → Disagree and Disable (menus vary). The launcher provides guidance and a shortcut to TV privacy settings, but cannot silently revoke Sony/Samba consent. No arbitrary Android setting values or privileged package-disable commands are applied.


## Capture and tag test

Capture starts only after the user starts Ad protection and grants Android screen-sharing consent and display-over-other-apps access. Monitoring continues every three seconds until stopped or Android ends sharing; it does not start at boot. Only the latest 30 screenshots/crops and matching JSON records are retained. Event logs rotate with one previous segment. Ad detection attempts media mute and shows an inset black cover. Two consecutive fresh badge-absent readings restore audio. Back/OK while covered, explicit Stop, and projection ending remove the cover and restore audio muted by the guard. An existing mute is preserved. A watchdog exits if fresh OCR stops for 12 seconds while covered. Audio/overlay actions and errors are included in local logs.

Screenshots, recognized text (including possible profile names/titles), positions, timestamps and diagnostic tags are saved locally in app-specific external storage. The previous session’s generated files are replaced when a new test starts. Pulling the directory through ADB transfers those files to your own computer. The app does not upload screenshots or text to an inference service. The bundled Google ML Kit SDK processes OCR on-device and may collect SDK diagnostics under Google’s terms; this is not a claim that the third-party SDK has zero telemetry. No general vision model is used.
