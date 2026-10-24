import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RuntimeEvidence } from '../components/RuntimeEvidence.jsx';

const metadata = {
  runtime: {
    managedBy: 'telemetry-connector',
    connectorVersion: 'phase6-v1',
    observedAt: '2026-10-14T09:50:00Z',
    health: { available: true, status: 'UP' },
    counters: { 'checkout.success': 4 },
    limitations: ['health only'],
  },
};

describe('RuntimeEvidence', () => {
  it('shows stored source, observation, health, counters, and limitations', () => {
    render(<RuntimeEvidence metadata={metadata} />);

    expect(screen.getByText('Stored observation')).toBeInTheDocument();
    expect(screen.getByText(/telemetry-connector/)).toBeInTheDocument();
    expect(screen.getByText(/2026-10-14 09:50:00 UTC/)).toBeInTheDocument();
    expect(screen.getByText('UP')).toBeInTheDocument();
    expect(screen.getByText('checkout.success')).toBeInTheDocument();
    expect(screen.getByText('health only')).toBeInTheDocument();
    expect(screen.getByText(/not live telemetry/i)).toBeInTheDocument();
  });

  it('distinguishes missing counters from a valid empty counter set', () => {
    const withoutCounters = { ...metadata.runtime };
    delete withoutCounters.counters;

    const { rerender } = render(
      <RuntimeEvidence metadata={{ runtime: withoutCounters }} />,
    );

    expect(
      screen.getByText(/counter data was not provided in the last observation/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/no counters were recorded in the last observation/i),
    ).not.toBeInTheDocument();

    rerender(
      <RuntimeEvidence metadata={{ runtime: { ...metadata.runtime, counters: {} } }} />,
    );

    expect(
      screen.getByText(/no counters were recorded in the last observation/i),
    ).toBeInTheDocument();
  });

  it('reports malformed counters without claiming absence and keeps available evidence', () => {
    render(
      <RuntimeEvidence
        metadata={{ runtime: { ...metadata.runtime, counters: 'not-an-object' } }}
      />,
    );

    expect(
      screen.getByText(/counter data could not be interpreted/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/no counters were recorded in the last observation/i),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/telemetry-connector/)).toBeInTheDocument();
    expect(screen.getByText(/2026-10-14 09:50:00 UTC/)).toBeInTheDocument();
  });

  it('keeps valid counters and flags a partially malformed counter set', () => {
    render(
      <RuntimeEvidence
        metadata={{
          runtime: {
            ...metadata.runtime,
            counters: { 'checkout.success': 4, invalid: 'many' },
          },
        }}
      />,
    );

    expect(screen.getByText('checkout.success')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(
      screen.getByText(/some counter values were not finite numbers/i),
    ).toBeInTheDocument();
  });

  it('does not claim an empty limitations list when the metadata is malformed', () => {
    const { rerender } = render(
      <RuntimeEvidence
        metadata={{ runtime: { ...metadata.runtime, limitations: 'not-an-array' } }}
      />,
    );

    expect(
      screen.getByText(/limitation metadata could not be interpreted/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/limitations metadata was malformed and is not shown/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/no limitations were stored with this observation/i),
    ).not.toBeInTheDocument();

    rerender(
      <RuntimeEvidence
        metadata={{ runtime: { ...metadata.runtime, limitations: [123, '', null] } }}
      />,
    );

    expect(
      screen.getByText(/limitation metadata could not be interpreted/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/limitations metadata contained no valid entries/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/no limitations were stored with this observation/i),
    ).not.toBeInTheDocument();
  });

  it('keeps valid limitations and warns when some entries are invalid', () => {
    render(
      <RuntimeEvidence
        metadata={{ runtime: { ...metadata.runtime, limitations: ['health only', 123] } }}
      />,
    );

    expect(screen.getByText('health only')).toBeInTheDocument();
    expect(
      screen.getByText(/some limitation values were invalid and are not shown/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/no limitations were stored with this observation/i),
    ).not.toBeInTheDocument();
  });

  it('shows absence only for missing or valid empty limitations metadata', () => {
    const withoutLimitations = { ...metadata.runtime };
    delete withoutLimitations.limitations;

    const { rerender } = render(
      <RuntimeEvidence metadata={{ runtime: withoutLimitations }} />,
    );

    expect(
      screen.getByText(/limitation data was not provided in the last observation/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/no limitations were stored with this observation/i),
    ).not.toBeInTheDocument();

    rerender(
      <RuntimeEvidence
        metadata={{ runtime: { ...metadata.runtime, limitations: [] } }}
      />,
    );

    expect(
      screen.getByText(/no limitations were stored with this observation/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/limitation metadata could not be interpreted/i),
    ).not.toBeInTheDocument();
  });

  it('distinguishes malformed source metadata from an absent source', () => {
    const withoutSource = { ...metadata.runtime };
    delete withoutSource.managedBy;

    const { rerender } = render(
      <RuntimeEvidence metadata={{ runtime: withoutSource }} />,
    );

    expect(screen.getByText('Not provided')).toBeInTheDocument();
    expect(
      screen.queryByText(/source unavailable \(malformed metadata\)/i),
    ).not.toBeInTheDocument();

    rerender(
      <RuntimeEvidence
        metadata={{ runtime: { ...metadata.runtime, managedBy: 42 } }}
      />,
    );

    expect(
      screen.getByText(/source unavailable \(malformed metadata\)/i),
    ).toBeInTheDocument();
    expect(screen.queryByText('Not provided')).not.toBeInTheDocument();
  });

  it('states when no runtime evidence is stored', () => {
    render(<RuntimeEvidence metadata={{ owner: 'team-payments' }} />);

    expect(screen.getByText(/no runtime evidence is stored/i)).toBeInTheDocument();
    expect(screen.queryByText('UP')).not.toBeInTheDocument();
    expect(screen.queryByText('DOWN')).not.toBeInTheDocument();
  });

  it('marks malformed runtime metadata as unrecognized without a health claim', () => {
    render(
      <RuntimeEvidence
        metadata={{
          runtime: {
            managedBy: 'telemetry-connector',
            observedAt: 'not-a-timestamp',
          },
        }}
      />,
    );

    expect(
      screen.getByText(/runtime metadata is present but could not be interpreted/i),
    ).toBeInTheDocument();
    expect(screen.queryByText('UP')).not.toBeInTheDocument();
    expect(screen.queryByText('DOWN')).not.toBeInTheDocument();
  });

  it('shows unavailable health without inferring a failure status', () => {
    render(
      <RuntimeEvidence
        metadata={{
          runtime: {
            ...metadata.runtime,
            health: { available: false, status: null },
          },
        }}
      />,
    );

    expect(
      screen.getByText(/health unavailable in the last observation/i),
    ).toBeInTheDocument();
    expect(screen.queryByText('DOWN')).not.toBeInTheDocument();
  });
});
