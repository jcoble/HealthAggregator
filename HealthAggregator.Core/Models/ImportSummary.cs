namespace HealthAggregator.Core.Models;

public sealed record ImportSummary(
    int SourceRecordsUpserted,
    int LabObservationsUpserted,
    int DiagnosticReportsUpserted,
    int PatientsUpserted,
    int ConditionsUpserted,
    int MedicationsUpserted,
    int AllergiesUpserted,
    int EncountersUpserted,
    int DocumentsUpserted)
{
    public static ImportSummary Empty { get; } = new(0, 0, 0, 0, 0, 0, 0, 0, 0);
}

public sealed record FhirResourceSyncSummary(
    string ResourceType,
    string RequestPath,
    ImportSummary Summary);

public sealed record EpicSyncSummary(
    ImportSummary Total,
    IReadOnlyList<FhirResourceSyncSummary> Resources)
{
    public static EpicSyncSummary FromResources(IEnumerable<FhirResourceSyncSummary> resources)
    {
        var resourceList = resources.ToList();
        var total = resourceList.Aggregate(ImportSummary.Empty, Add);

        return new EpicSyncSummary(total, resourceList);
    }

    private static ImportSummary Add(ImportSummary left, ImportSummary right) =>
        new(
            left.SourceRecordsUpserted + right.SourceRecordsUpserted,
            left.LabObservationsUpserted + right.LabObservationsUpserted,
            left.DiagnosticReportsUpserted + right.DiagnosticReportsUpserted,
            left.PatientsUpserted + right.PatientsUpserted,
            left.ConditionsUpserted + right.ConditionsUpserted,
            left.MedicationsUpserted + right.MedicationsUpserted,
            left.AllergiesUpserted + right.AllergiesUpserted,
            left.EncountersUpserted + right.EncountersUpserted,
            left.DocumentsUpserted + right.DocumentsUpserted);

    private static ImportSummary Add(ImportSummary left, FhirResourceSyncSummary right) =>
        Add(left, right.Summary);
}
