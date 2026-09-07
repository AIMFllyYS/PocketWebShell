# Browser presentation and session boundaries

`BrowserScreen` collects read-only feature state and connects independent production components.
`BrowserChromeController` owns only transient toolbar/Dock presentation; app owns the actual single
glass Dock/orb/edge surface. Its Saver restores the bottom shape and toolbar visibility, not an active
session, open menu, keyboard, permission request or interaction lock. With no surviving page, the
environment reducer returns to reachable expanded navigation.

`rememberWebSessionRequests(sessionId, visible, onNewWindow, onMessage)` and
`WebSessionDialogs(requests, onRetry, onLeave)` are the shared platform-request facade for both the
multi-tab browser and app's single-site shell. Pass `requests.listener` to `ShellWebViewHost`; `busy`
can pause chrome motion while an upload, website permission or certificate warning is open. Android
objects remain in the current session scope. Results return to their launch-time request owner, and
navigation, detachment or session changes invalidate pending requests. Certificate retry always
performs normal validation; the facade never bypasses it.

`ShellWebViewHost` is the native ownership/lifecycle boundary. Hosts receiving consumed Compose safe
insets use `parentHandlesInsets = true`; standalone shells may retain native PAD/CSS_ONLY handling.
Only the visible host holds ephemeral UI callbacks. Per-session listeners remain attached
for background tab metadata, independently of chrome state.

The bottom Dock collapses to the orb as soon as a page session becomes active, and again after an
in-tab navigation; no scroll or touch classification is involved, and web content can never trigger
or veto the transition. A deliberate reveal (tap the orb or the parked edge handle) holds until the
session or URL changes, and the menu keeps an explicit collapse command.

`browserCatalog()` supplies real stateless production content with in-memory fixtures for the app's
Playbook. These samples do not construct business ViewModels, WebViews or database writers. Saved
page persistence stays in `BrowserSavedPagesRepository`, outside the feature's presentation layer.
