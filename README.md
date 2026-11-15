# SafePlay BD

A simple Android DNS protection app focused on blocking known casino, betting and gambling domains.

## First milestone
- Native Kotlin app with a simple protection toggle.
- Local VPN-based DNS filtering using Android's VPN permission.
- Starter domain deny-list for exact domains and their subdomains.
- GitHub Actions debug APK build.

## Important limitations
- This milestone is an early prototype and has not yet been built or device-tested.
- DNS filtering blocks only domains in the configured list. It cannot reliably classify every new gambling site or inspect encrypted HTTPS page content.
- Some advertising uses shared or first-party domains and cannot safely be blocked by domain alone.
- Android normally allows only one active VPN at a time.
- Uninstalling this app stops its VPN service. Separately configured Private DNS may provide additional protection, but this app does not silently enforce it.
- The initial list is a starter list, not a comprehensive threat feed.

## Build
GitHub Actions builds the debug APK on pushes and pull requests to main.

## Install and test
Install the debug APK on a test Android device, open SafePlay BD, approve the Android VPN permission, and enable protection. Test normal browsing and configured blocked domains before relying on it.

## Privacy
Allowed DNS queries are forwarded to Cloudflare DNS (1.1.1.1). Blocked queries are answered locally. This initial app does not upload browsing history.
