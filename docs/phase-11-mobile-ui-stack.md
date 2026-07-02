# Phase 11 Mobile UI Stack

This slice migrates the readiness UI from the static web prototype to the
requested mobile stack:

- React Native TypeScript app shell through Expo;
- Tailwind CSS styling through NativeWind class names;
- Redux Toolkit readiness slice and React Redux provider;
- mobile-first readiness dashboard components;
- dependency-free Node tests for the TypeScript readiness model and mobile
  class tokens;
- CI updated to run the mobile UI test suite instead of the old static web UI
  test path.

The mobile app still uses deterministic local readiness data. Production follow
up work should connect it to authenticated API endpoints, add real navigation,
and run device-level tests after dependencies are installed.

## TDD Notes

- **Red:** mobile UI tests first referenced the missing readiness model and
  failed again when the test total changed from 84 to 85.
- **Green:** the React Native TypeScript app scaffold, NativeWind/Tailwind
  config, Redux slice, selectors, and readiness model made the tests pass.
- **Refactor:** the static `apps/web` dashboard was removed so the codebase has
  one correct UI stack.
