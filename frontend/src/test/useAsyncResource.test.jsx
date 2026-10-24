import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useAsyncResource } from '../hooks/useAsyncResource.js';

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

describe('useAsyncResource', () => {
  it('ignores an older success that settles after a newer failure', async () => {
    const older = deferred();
    const newer = deferred();
    const loads = [older.promise, newer.promise];
    const loader = vi.fn(() => loads.shift());
    const { result } = renderHook(() => useAsyncResource(loader, []));

    await waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    act(() => {
      result.current.reload();
    });
    expect(loader).toHaveBeenCalledTimes(2);

    await act(async () => {
      newer.reject(new Error('new failure'));
    });
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(async () => {
      older.resolve('stale success');
    });
    await flush();

    expect(result.current.status).toBe('error');
    expect(result.current.data).toBeNull();
    expect(result.current.error).toMatchObject({ message: 'new failure' });
  });

  it('ignores an older failure that settles after a newer success', async () => {
    const older = deferred();
    const newer = deferred();
    const loads = [older.promise, newer.promise];
    const loader = vi.fn(() => loads.shift());
    const { result } = renderHook(() => useAsyncResource(loader, []));

    await waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    act(() => {
      result.current.reload();
    });

    await act(async () => {
      newer.resolve('new success');
    });
    await waitFor(() => expect(result.current.status).toBe('success'));

    await act(async () => {
      older.reject(new Error('stale failure'));
    });
    await flush();

    expect(result.current.status).toBe('success');
    expect(result.current.data).toBe('new success');
    expect(result.current.error).toBeNull();
  });
});
