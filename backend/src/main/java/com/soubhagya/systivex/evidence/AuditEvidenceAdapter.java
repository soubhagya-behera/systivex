package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.proposal.api.AuditEventResponse;
import com.soubhagya.systivex.proposal.service.ProposalService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AuditEvidenceAdapter implements EvidenceSource<List<AuditEventResponse>> {

    private static final Comparator<AuditEventResponse> ORDER =
            Comparator.comparingLong(AuditEventResponse::seq)
                    .thenComparing(event -> event.id() == null ? "" : event.id().toString());

    public AuditEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(List<AuditEventResponse> events) {
        return adapt(events, false, 0);
    }

    public List<EvidenceItem<?>> adapt(
            List<AuditEventResponse> events, boolean truncated) {
        return adapt(events, truncated, 0);
    }

    public List<EvidenceItem<?>> adapt(ProposalService.BoundedAuditTrail bounded) {
        if (bounded == null) {
            return List.of();
        }
        return adapt(bounded.events(), bounded.truncated(), bounded.omittedCount());
    }

    private List<EvidenceItem<?>> adapt(
            List<AuditEventResponse> events, boolean truncated, int omittedHint) {
        EvidenceItem<AuditEvidence> item = adaptItem(events, truncated, omittedHint);
        return item == null ? List.of() : List.of(item);
    }

    public static EvidenceItem<AuditEvidence> adaptItem(
            List<AuditEventResponse> events, boolean truncated) {
        return adaptItem(events, truncated, 0);
    }

    public static EvidenceItem<AuditEvidence> adaptItem(
            List<AuditEventResponse> events, boolean truncated, int omittedHint) {
        if (events == null) {
            return null;
        }
        int provided = nonNullCount(events);
        List<AuditEventResponse> sorted = new ArrayList<>();
        for (AuditEventResponse event : events) {
            if (event != null) {
                sorted.add(event);
            }
        }
        sorted.sort(ORDER);
        List<AuditEventResponse> mapped = EvidenceLimits.first(sorted, EvidenceLimits.MAX_AUDIT_EVENTS);

        boolean actualTruncated = truncated || provided > mapped.size();
        int omitted = Math.max(
                Math.max(provided - mapped.size(), omittedHint),
                actualTruncated ? 1 : 0);
        AuditEvidence content = new AuditEvidence(
                map(mapped), actualTruncated, EvidenceLimits.MAX_AUDIT_EVENTS, omitted);

        List<String> limitations = new ArrayList<>();
        if (provided == 0) {
            limitations.add("No audit events found for the requested proposal.");
        }
        if (actualTruncated) {
            limitations.add("Per-proposal audit events capped at "
                    + EvidenceLimits.MAX_AUDIT_EVENTS + "; " + omitted
                    + " additional events omitted.");
        }
        List<String> finalLimitations = EvidenceLimits.textList(
                limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH).values();

        List<EvidenceTimestamp> timestamps = new ArrayList<>();
        if (!mapped.isEmpty() && mapped.get(0).occurredAt() != null) {
            timestamps.add(new EvidenceTimestamp("firstOccurredAt", mapped.get(0).occurredAt()));
        }
        if (!mapped.isEmpty() && mapped.get(mapped.size() - 1).occurredAt() != null) {
            timestamps.add(new EvidenceTimestamp(
                    "lastOccurredAt", mapped.get(mapped.size() - 1).occurredAt()));
        }

        EvidenceSourceStatus status;
        EvidenceQualification qualification;
        if (provided == 0) {
            status = EvidenceSourceStatus.UNAVAILABLE;
            qualification = EvidenceQualification.MISSING;
        } else {
            status = actualTruncated
                    ? EvidenceSourceStatus.TRUNCATED
                    : EvidenceSourceStatus.AVAILABLE;
            qualification = EvidenceQualification.HISTORICAL;
        }

        return new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.AUDIT_EVIDENCE,
                "audit.events",
                provided == 0
                        ? "audit:none"
                        : "audit:" + (mapped.get(0).proposalKey() == null
                                ? "unknown"
                                : mapped.get(0).proposalKey()),
                null,
                timestamps,
                content,
                finalLimitations,
                qualification,
                status,
                mapped.size(),
                omitted);
    }

    public static AuditEvidence toContent(
            List<AuditEventResponse> events, boolean truncated, int omittedHint) {
        EvidenceItem<AuditEvidence> item = adaptItem(events, truncated, omittedHint);
        return item == null ? null : item.content();
    }

    private static List<AuditEventEvidence> map(List<AuditEventResponse> events) {
        List<AuditEventEvidence> mapped = new ArrayList<>();
        for (AuditEventResponse event : events) {
            mapped.add(new AuditEventEvidence(
                    event.id(),
                    event.seq(),
                    event.occurredAt(),
                    event.action(),
                    event.outcome(),
                    event.proposalKey(),
                    event.revisionNumber(),
                    event.operationsHash(),
                    event.assessmentHash(),
                    event.prevHash(),
                    event.eventHash()));
        }
        return List.copyOf(mapped);
    }

    private static int nonNullCount(List<AuditEventResponse> events) {
        int count = 0;
        for (AuditEventResponse event : events) {
            if (event != null) {
                count++;
            }
        }
        return count;
    }
}
