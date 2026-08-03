package mindmap84;

/**
 * App + update settings. EDIT THESE once your GitHub repo exists.
 *
 * Releases are matched by tag (e.g. a git tag "v1.1.0"), and the updater
 * downloads the release asset whose name equals ASSET_NAME. So your GitHub
 * release-publishing flow is simply:
 *   1. bump VERSION below and rebuild (build.ps1 -> MINDMAP84-windows.zip)
 *   2. create a GitHub Release tagged  v<VERSION>
 *   3. attach  MINDMAP84-windows.zip  as an asset
 */
final class Config {
    /** Current app version. Keep in sync with the git tag (tag = "v" + VERSION). */
    static final String VERSION = "0.1.0";

    /** GitHub account + repo that hosts the releases (must be PUBLIC). */
    static final String GITHUB_OWNER = "UIForSquid";
    static final String GITHUB_REPO  = "84";

    /** The release asset the updater downloads (produced by the build). */
    static final String ASSET_NAME = "MINDMAP84-windows.zip";

    static final String APP_NAME = "MINDMAP84";
    static final String EXE_NAME = "MINDMAP84.exe";

    /** GitHub REST endpoint for the newest published release. */
    static String latestReleaseApi() {
        return "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    }

    /** True until the placeholder owner is replaced with a real value. */
    static boolean isConfigured() {
        return GITHUB_OWNER != null && !GITHUB_OWNER.startsWith("YOUR_");
    }

    private Config() {}
}
