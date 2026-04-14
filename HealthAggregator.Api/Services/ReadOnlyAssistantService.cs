using HealthAggregator.Data;
using HealthAggregator.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Services;

public sealed class ReadOnlyAssistantService(HealthAggregatorDbContext db)
{
    public async Task<AssistantReply> ReplyAsync(string message, CancellationToken cancellationToken)
    {
        var normalized = message.Trim();
        var lowered = normalized.ToLowerInvariant();

        var thread = new AiThread
        {
            Title = normalized.Length > 60 ? normalized[..60] : normalized,
            CreatedAt = DateTimeOffset.UtcNow
        };
        db.AiThreads.Add(thread);
        await db.SaveChangesAsync(cancellationToken);

        db.AiMessages.Add(new AiMessage
        {
            AiThreadId = thread.Id,
            Role = "user",
            Content = normalized,
            CreatedAt = DateTimeOffset.UtcNow
        });

        var labs = (await db.LabObservations
            .AsNoTracking()
            .ToListAsync(cancellationToken))
            .OrderBy(lab => lab.EffectiveAt)
            .ToList();

        var relevant = SelectRelevantLabs(labs, lowered).ToList();
        var answer = BuildAnswer(relevant, labs, lowered);

        db.AiMessages.Add(new AiMessage
        {
            AiThreadId = thread.Id,
            Role = "assistant",
            Content = answer,
            CreatedAt = DateTimeOffset.UtcNow
        });
        await db.SaveChangesAsync(cancellationToken);

        var citations = relevant
            .Take(8)
            .Select(lab => new AssistantCitation(
                lab.FhirReference,
                lab.SourceName,
                lab.EffectiveAt,
                lab.TestName,
                lab.NumericValue?.ToString("0.######") ?? lab.TextValue ?? "no value",
                lab.Unit))
            .ToList();

        return new AssistantReply(thread.Id, answer, citations);
    }

    private static IEnumerable<LabObservation> SelectRelevantLabs(IEnumerable<LabObservation> labs, string lowered)
    {
        if (lowered.Contains("a1c") || lowered.Contains("hemoglobin"))
        {
            return labs.Where(lab => Contains(lab, "a1c") || lab.LoincCode == "4548-4");
        }

        if (lowered.Contains("ldl"))
        {
            return labs.Where(lab => Contains(lab, "ldl") || lab.LoincCode is "13457-7" or "18262-6");
        }

        if (lowered.Contains("abnormal") || lowered.Contains("high") || lowered.Contains("low"))
        {
            return labs.Where(IsAbnormal);
        }

        return labs.TakeLast(12);
    }

    private static string BuildAnswer(IReadOnlyList<LabObservation> relevant, IReadOnlyList<LabObservation> allLabs, string lowered)
    {
        if (allLabs.Count == 0)
        {
            return "I do not have lab data yet. Connect MyChart or upload a FHIR export, then I can summarize trends with source dates and values.";
        }

        if (relevant.Count == 0)
        {
            return "I did not find matching labs in the local record. I only searched the normalized read-only lab table, so this may mean the result has not been synced or is only present in an attached document.";
        }

        if (lowered.Contains("abnormal") || lowered.Contains("high") || lowered.Contains("low"))
        {
            var lines = relevant
                .OrderByDescending(lab => lab.EffectiveAt)
                .Take(8)
                .Select(FormatLabLine);
            return "Abnormal or flagged labs I found:\n" + string.Join("\n", lines) + "\n\nThis is not a diagnosis. Use these dated values as a source-backed list to review with your clinician.";
        }

        var first = relevant.First();
        var last = relevant.Last();
        var trend = first.NumericValue.HasValue && last.NumericValue.HasValue
            ? $" The first value I found was {FormatValue(first)} on {FormatDate(first.EffectiveAt)}, and the latest was {FormatValue(last)} on {FormatDate(last.EffectiveAt)}."
            : "";

        var recent = relevant
            .OrderByDescending(lab => lab.EffectiveAt)
            .Take(6)
            .OrderBy(lab => lab.EffectiveAt)
            .Select(FormatLabLine);

        return $"I found {relevant.Count} matching lab result(s).{trend}\n\nRecent values:\n{string.Join("\n", recent)}\n\nThis is read-only analysis from your local records, not medical advice.";
    }

    private static bool IsAbnormal(LabObservation lab)
    {
        if (!string.IsNullOrWhiteSpace(lab.Interpretation))
        {
            return true;
        }

        return lab.NumericValue.HasValue
            && ((lab.ReferenceLow.HasValue && lab.NumericValue < lab.ReferenceLow)
                || (lab.ReferenceHigh.HasValue && lab.NumericValue > lab.ReferenceHigh));
    }

    private static bool Contains(LabObservation lab, string value) =>
        lab.TestName.Contains(value, StringComparison.OrdinalIgnoreCase)
        || (lab.LoincCode?.Contains(value, StringComparison.OrdinalIgnoreCase) ?? false);

    private static string FormatLabLine(LabObservation lab) =>
        $"- {FormatDate(lab.EffectiveAt)}: {lab.TestName} {FormatValue(lab)} from {lab.SourceName}{FormatFlag(lab)}";

    private static string FormatValue(LabObservation lab) =>
        $"{lab.NumericValue?.ToString("0.######") ?? lab.TextValue ?? "no value"}{(string.IsNullOrWhiteSpace(lab.Unit) ? "" : $" {lab.Unit}")}";

    private static string FormatDate(DateTimeOffset? date) =>
        date?.ToString("yyyy-MM-dd") ?? "unknown date";

    private static string FormatFlag(LabObservation lab) =>
        string.IsNullOrWhiteSpace(lab.Interpretation) ? "" : $" ({lab.Interpretation})";
}

public sealed record AssistantReply(int ThreadId, string Message, IReadOnlyList<AssistantCitation> Citations);

public sealed record AssistantCitation(
    string FhirReference,
    string SourceName,
    DateTimeOffset? EffectiveAt,
    string TestName,
    string Value,
    string? Unit);
