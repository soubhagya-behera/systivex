/**
 * Development-only gate for the local synthetic preview.
 *
 * This module deliberately contains no sample records. The synthetic data is
 * loaded through a development-only dynamic import so production builds cannot
 * serve it. Preview is not a backend fake, an identity provider, or an
 * authentication bypass.
 */
export function canUsePreview(env) {
  return env?.DEV === true && env?.PROD !== true;
}
