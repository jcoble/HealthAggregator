using System.Globalization;
using System.Text.Json;
using HealthAggregator.Core.Models;
using HealthAggregator.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Data.Services;

public sealed class FhirImportService(HealthAggregatorDbContext db)
{
    public async Task<ImportSummary> ImportBundleAsync(
        string sourceSystem,
        string sourceName,
        string payloadJson,
        CancellationToken cancellationToken)
    {
        using var document = JsonDocument.Parse(payloadJson);
        var resources = EnumerateResources(document.RootElement).ToArray();
        var now = DateTimeOffset.UtcNow;

        var syncJob = new SyncJob
        {
            SourceSystem = sourceSystem,
            SourceName = sourceName,
            StartedAt = now
        };
        db.SyncJobs.Add(syncJob);
        await db.SaveChangesAsync(cancellationToken);

        var sourceRecords = 0;
        var labs = 0;
        var reports = 0;
        var patients = 0;
        var conditions = 0;
        var medications = 0;
        var allergies = 0;
        var encounters = 0;
        var documents = 0;

        foreach (var resource in resources)
        {
            var resourceType = ReadString(resource, "resourceType");
            if (string.IsNullOrWhiteSpace(resourceType))
            {
                continue;
            }

            var resourceId = ReadString(resource, "id") ?? StableAnonymousId(resource.GetRawText());
            var fhirReference = $"{resourceType}/{resourceId}";
            await UpsertSourceRecordAsync(sourceSystem, sourceName, syncJob.Id, resource, resourceType, resourceId, fhirReference, now, cancellationToken);
            sourceRecords++;

            switch (resourceType)
            {
                case "Patient":
                    await UpsertPatientAsync(sourceSystem, resource, resourceId, now, cancellationToken);
                    patients++;
                    break;
                case "Observation" when IsLaboratoryObservation(resource):
                    await UpsertLabObservationAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    labs++;
                    break;
                case "DiagnosticReport":
                    await UpsertDiagnosticReportAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    reports++;
                    break;
                case "Condition":
                    await UpsertConditionAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    conditions++;
                    break;
                case "MedicationRequest":
                case "MedicationStatement":
                    await UpsertMedicationAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    medications++;
                    break;
                case "AllergyIntolerance":
                    await UpsertAllergyAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    allergies++;
                    break;
                case "Encounter":
                    await UpsertEncounterAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    encounters++;
                    break;
                case "DocumentReference":
                    await UpsertDocumentAsync(sourceSystem, sourceName, resource, resourceId, fhirReference, now, cancellationToken);
                    documents++;
                    break;
            }
        }

        syncJob.Status = "completed";
        syncJob.CompletedAt = DateTimeOffset.UtcNow;
        syncJob.SourceRecordsUpserted = sourceRecords;
        syncJob.LabObservationsUpserted = labs;
        await db.SaveChangesAsync(cancellationToken);

        return new ImportSummary(sourceRecords, labs, reports, patients, conditions, medications, allergies, encounters, documents);
    }

    private async Task UpsertSourceRecordAsync(
        string sourceSystem,
        string sourceName,
        int syncJobId,
        JsonElement resource,
        string resourceType,
        string resourceId,
        string fhirReference,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var record = await db.SourceRecords.SingleOrDefaultAsync(
            candidate => candidate.SourceSystem == sourceSystem
                && candidate.ResourceType == resourceType
                && candidate.ResourceId == resourceId,
            cancellationToken);

        if (record is null)
        {
            record = new SourceRecord
            {
                SourceSystem = sourceSystem,
                ResourceType = resourceType,
                ResourceId = resourceId
            };
            db.SourceRecords.Add(record);
        }

        record.SyncJobId = syncJobId;
        record.SourceName = sourceName;
        record.FhirReference = fhirReference;
        record.RawJson = resource.GetRawText();
        record.ImportedAt = now;
    }

    private async Task UpsertPatientAsync(
        string sourceSystem,
        JsonElement resource,
        string resourceId,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var patient = await db.Patients.SingleOrDefaultAsync(
            candidate => candidate.SourceSystem == sourceSystem && candidate.FhirId == resourceId,
            cancellationToken);

        if (patient is null)
        {
            patient = new PatientRecord { SourceSystem = sourceSystem, FhirId = resourceId };
            db.Patients.Add(patient);
        }

        patient.DisplayName = ReadHumanName(resource);
        patient.BirthDate = ReadDateOnly(resource, "birthDate");
        patient.UpdatedAt = now;
    }

    private async Task UpsertLabObservationAsync(
        string sourceSystem,
        string sourceName,
        JsonElement resource,
        string resourceId,
        string fhirReference,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var lab = await db.LabObservations.SingleOrDefaultAsync(
            candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference,
            cancellationToken);

        if (lab is null)
        {
            lab = new LabObservation { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.LabObservations.Add(lab);
        }

        var code = ReadCode(resource, "code");
        var value = ReadObservationValue(resource);
        var range = ReadReferenceRange(resource);

        lab.SourceName = sourceName;
        lab.ResourceId = resourceId;
        lab.PatientFhirId = ReadReferenceId(resource, "subject");
        lab.DiagnosticReportReference = ReadFirstReference(resource, "partOf");
        lab.LoincCode = code.LoincCode;
        lab.TestName = code.DisplayText ?? "Unnamed observation";
        lab.NumericValue = value.NumericValue;
        lab.TextValue = value.TextValue;
        lab.Unit = value.Unit;
        lab.ReferenceLow = range.Low;
        lab.ReferenceHigh = range.High;
        lab.ReferenceText = range.Text;
        lab.Interpretation = ReadInterpretation(resource);
        lab.EffectiveAt = ReadDateTimeOffset(resource, "effectiveDateTime") ?? ReadDateTimeOffset(resource, "issued");
        lab.Status = ReadString(resource, "status") ?? "";
        lab.ImportedAt = now;
    }

    private async Task UpsertDiagnosticReportAsync(
        string sourceSystem,
        string sourceName,
        JsonElement resource,
        string resourceId,
        string fhirReference,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var report = await db.DiagnosticReports.SingleOrDefaultAsync(
            candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference,
            cancellationToken);

        if (report is null)
        {
            report = new DiagnosticReportRecord { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.DiagnosticReports.Add(report);
        }

        report.SourceName = sourceName;
        report.ResourceId = resourceId;
        report.PatientFhirId = ReadReferenceId(resource, "subject");
        report.CodeText = ReadCode(resource, "code").DisplayText;
        report.Status = ReadString(resource, "status");
        report.IssuedAt = ReadDateTimeOffset(resource, "issued") ?? ReadDateTimeOffset(resource, "effectiveDateTime");
        report.ResultReferences = string.Join(";", ReadReferences(resource, "result"));
        report.ImportedAt = now;
    }

    private async Task UpsertConditionAsync(string sourceSystem, string sourceName, JsonElement resource, string resourceId, string fhirReference, DateTimeOffset now, CancellationToken cancellationToken)
    {
        var condition = await db.Conditions.SingleOrDefaultAsync(candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference, cancellationToken);
        if (condition is null)
        {
            condition = new ConditionRecord { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.Conditions.Add(condition);
        }

        condition.SourceName = sourceName;
        condition.ResourceId = resourceId;
        condition.PatientFhirId = ReadReferenceId(resource, "subject");
        condition.CodeText = ReadCode(resource, "code").DisplayText;
        condition.ClinicalStatus = ReadCode(resource, "clinicalStatus").DisplayText;
        condition.OnsetAt = ReadDateTimeOffset(resource, "onsetDateTime");
        condition.RecordedAt = ReadDateTimeOffset(resource, "recordedDate");
        condition.ImportedAt = now;
    }

    private async Task UpsertMedicationAsync(string sourceSystem, string sourceName, JsonElement resource, string resourceId, string fhirReference, DateTimeOffset now, CancellationToken cancellationToken)
    {
        var medication = await db.Medications.SingleOrDefaultAsync(candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference, cancellationToken);
        if (medication is null)
        {
            medication = new MedicationRecord { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.Medications.Add(medication);
        }

        medication.SourceName = sourceName;
        medication.ResourceId = resourceId;
        medication.PatientFhirId = ReadReferenceId(resource, "subject");
        medication.MedicationText = ReadCode(resource, "medicationCodeableConcept").DisplayText ?? ReadString(resource, "medicationReference");
        medication.Status = ReadString(resource, "status");
        medication.AuthoredAt = ReadDateTimeOffset(resource, "authoredOn") ?? ReadDateTimeOffset(resource, "effectiveDateTime");
        medication.ImportedAt = now;
    }

    private async Task UpsertAllergyAsync(string sourceSystem, string sourceName, JsonElement resource, string resourceId, string fhirReference, DateTimeOffset now, CancellationToken cancellationToken)
    {
        var allergy = await db.Allergies.SingleOrDefaultAsync(candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference, cancellationToken);
        if (allergy is null)
        {
            allergy = new AllergyRecord { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.Allergies.Add(allergy);
        }

        allergy.SourceName = sourceName;
        allergy.ResourceId = resourceId;
        allergy.PatientFhirId = ReadReferenceId(resource, "patient");
        allergy.AllergyText = ReadCode(resource, "code").DisplayText;
        allergy.ClinicalStatus = ReadCode(resource, "clinicalStatus").DisplayText;
        allergy.RecordedAt = ReadDateTimeOffset(resource, "recordedDate");
        allergy.ImportedAt = now;
    }

    private async Task UpsertEncounterAsync(string sourceSystem, string sourceName, JsonElement resource, string resourceId, string fhirReference, DateTimeOffset now, CancellationToken cancellationToken)
    {
        var encounter = await db.Encounters.SingleOrDefaultAsync(candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference, cancellationToken);
        if (encounter is null)
        {
            encounter = new EncounterRecord { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.Encounters.Add(encounter);
        }

        encounter.SourceName = sourceName;
        encounter.ResourceId = resourceId;
        encounter.PatientFhirId = ReadReferenceId(resource, "subject");
        encounter.TypeText = ReadFirstCodeableConcept(resource, "type");
        encounter.Status = ReadString(resource, "status");
        encounter.StartedAt = ReadNestedDateTimeOffset(resource, "period", "start");
        encounter.EndedAt = ReadNestedDateTimeOffset(resource, "period", "end");
        encounter.ImportedAt = now;
    }

    private async Task UpsertDocumentAsync(string sourceSystem, string sourceName, JsonElement resource, string resourceId, string fhirReference, DateTimeOffset now, CancellationToken cancellationToken)
    {
        var document = await db.Documents.SingleOrDefaultAsync(candidate => candidate.SourceSystem == sourceSystem && candidate.FhirReference == fhirReference, cancellationToken);
        if (document is null)
        {
            document = new DocumentRecord { SourceSystem = sourceSystem, FhirReference = fhirReference };
            db.Documents.Add(document);
        }

        document.SourceName = sourceName;
        document.ResourceId = resourceId;
        document.PatientFhirId = ReadReferenceId(resource, "subject");
        document.TypeText = ReadCode(resource, "type").DisplayText;
        document.Status = ReadString(resource, "status");
        document.DocumentedAt = ReadDateTimeOffset(resource, "date");
        document.ContentUrl = ReadFirstAttachmentUrl(resource);
        document.ImportedAt = now;
    }

    private static IEnumerable<JsonElement> EnumerateResources(JsonElement root)
    {
        if (ReadString(root, "resourceType") == "Bundle" && root.TryGetProperty("entry", out var entries) && entries.ValueKind == JsonValueKind.Array)
        {
            foreach (var entry in entries.EnumerateArray())
            {
                if (entry.TryGetProperty("resource", out var resource) && resource.ValueKind == JsonValueKind.Object)
                {
                    yield return resource.Clone();
                }
            }
        }
        else if (root.ValueKind == JsonValueKind.Object)
        {
            yield return root.Clone();
        }
    }

    private static bool IsLaboratoryObservation(JsonElement resource)
    {
        if (!resource.TryGetProperty("category", out var categories) || categories.ValueKind != JsonValueKind.Array)
        {
            return true;
        }

        return categories.EnumerateArray()
            .SelectMany(category => category.TryGetProperty("coding", out var coding) && coding.ValueKind == JsonValueKind.Array
                ? coding.EnumerateArray()
                : [])
            .Any(coding => string.Equals(ReadString(coding, "code"), "laboratory", StringComparison.OrdinalIgnoreCase));
    }

    private static string? ReadHumanName(JsonElement resource)
    {
        if (!resource.TryGetProperty("name", out var names) || names.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        var firstName = names.EnumerateArray().FirstOrDefault();
        if (firstName.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        var given = firstName.TryGetProperty("given", out var givenValues) && givenValues.ValueKind == JsonValueKind.Array
            ? string.Join(" ", givenValues.EnumerateArray().Select(value => value.GetString()).Where(value => !string.IsNullOrWhiteSpace(value)))
            : null;
        var family = ReadString(firstName, "family");

        return string.Join(" ", new[] { given, family }.Where(value => !string.IsNullOrWhiteSpace(value)));
    }

    private static (string? LoincCode, string? DisplayText) ReadCode(JsonElement resource, string propertyName)
    {
        if (!resource.TryGetProperty(propertyName, out var code) || code.ValueKind != JsonValueKind.Object)
        {
            return (null, null);
        }

        var text = ReadString(code, "text");
        string? firstDisplay = null;
        string? loinc = null;

        if (code.TryGetProperty("coding", out var coding) && coding.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in coding.EnumerateArray())
            {
                firstDisplay ??= ReadString(item, "display") ?? ReadString(item, "code");
                if (string.Equals(ReadString(item, "system"), "http://loinc.org", StringComparison.OrdinalIgnoreCase))
                {
                    loinc = ReadString(item, "code");
                    firstDisplay = ReadString(item, "display") ?? firstDisplay;
                }
            }
        }

        return (loinc, text ?? firstDisplay);
    }

    private static (decimal? NumericValue, string? TextValue, string? Unit) ReadObservationValue(JsonElement resource)
    {
        if (resource.TryGetProperty("valueQuantity", out var quantity) && quantity.ValueKind == JsonValueKind.Object)
        {
            return (ReadDecimal(quantity, "value"), null, ReadString(quantity, "unit") ?? ReadString(quantity, "code"));
        }

        return (
            null,
            ReadString(resource, "valueString") ?? ReadString(resource, "valueCodeableConcept"),
            null);
    }

    private static (decimal? Low, decimal? High, string? Text) ReadReferenceRange(JsonElement resource)
    {
        if (!resource.TryGetProperty("referenceRange", out var ranges) || ranges.ValueKind != JsonValueKind.Array)
        {
            return (null, null, null);
        }

        var firstRange = ranges.EnumerateArray().FirstOrDefault();
        if (firstRange.ValueKind != JsonValueKind.Object)
        {
            return (null, null, null);
        }

        return (
            ReadNestedDecimal(firstRange, "low", "value"),
            ReadNestedDecimal(firstRange, "high", "value"),
            ReadString(firstRange, "text"));
    }

    private static string? ReadInterpretation(JsonElement resource)
    {
        if (!resource.TryGetProperty("interpretation", out var interpretations) || interpretations.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var interpretation in interpretations.EnumerateArray())
        {
            var code = ReadCodeableConceptDisplay(interpretation);
            if (!string.IsNullOrWhiteSpace(code))
            {
                return code;
            }
        }

        return null;
    }

    private static string? ReadFirstCodeableConcept(JsonElement resource, string propertyName)
    {
        if (!resource.TryGetProperty(propertyName, out var concepts) || concepts.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        return concepts.EnumerateArray()
            .Select(ReadCodeableConceptDisplay)
            .FirstOrDefault(value => !string.IsNullOrWhiteSpace(value));
    }

    private static string? ReadCodeableConceptDisplay(JsonElement concept)
    {
        var text = ReadString(concept, "text");
        if (!string.IsNullOrWhiteSpace(text))
        {
            return text;
        }

        if (!concept.TryGetProperty("coding", out var coding) || coding.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        return coding.EnumerateArray()
            .Select(item => ReadString(item, "display") ?? ReadString(item, "code"))
            .FirstOrDefault(value => !string.IsNullOrWhiteSpace(value));
    }

    private static IEnumerable<string> ReadReferences(JsonElement resource, string propertyName)
    {
        if (!resource.TryGetProperty(propertyName, out var references) || references.ValueKind != JsonValueKind.Array)
        {
            yield break;
        }

        foreach (var reference in references.EnumerateArray())
        {
            var value = ReadString(reference, "reference");
            if (!string.IsNullOrWhiteSpace(value))
            {
                yield return value;
            }
        }
    }

    private static string? ReadFirstReference(JsonElement resource, string propertyName) =>
        ReadReferences(resource, propertyName).FirstOrDefault();

    private static string? ReadReferenceId(JsonElement resource, string propertyName)
    {
        if (!resource.TryGetProperty(propertyName, out var reference))
        {
            return null;
        }

        var value = ReadString(reference, "reference");
        return value?.Split('/', StringSplitOptions.RemoveEmptyEntries).LastOrDefault();
    }

    private static string? ReadFirstAttachmentUrl(JsonElement resource)
    {
        if (!resource.TryGetProperty("content", out var contents) || contents.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var content in contents.EnumerateArray())
        {
            if (content.TryGetProperty("attachment", out var attachment))
            {
                var url = ReadString(attachment, "url");
                if (!string.IsNullOrWhiteSpace(url))
                {
                    return url;
                }
            }
        }

        return null;
    }

    private static DateOnly? ReadDateOnly(JsonElement resource, string propertyName)
    {
        var value = ReadString(resource, propertyName);
        return DateOnly.TryParse(value, CultureInfo.InvariantCulture, DateTimeStyles.None, out var parsed)
            ? parsed
            : null;
    }

    private static DateTimeOffset? ReadNestedDateTimeOffset(JsonElement resource, string parentName, string propertyName)
    {
        return resource.TryGetProperty(parentName, out var parent)
            ? ReadDateTimeOffset(parent, propertyName)
            : null;
    }

    private static DateTimeOffset? ReadDateTimeOffset(JsonElement resource, string propertyName)
    {
        var value = ReadString(resource, propertyName);
        return DateTimeOffset.TryParse(value, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var parsed)
            ? parsed.ToUniversalTime()
            : null;
    }

    private static decimal? ReadNestedDecimal(JsonElement resource, string parentName, string propertyName)
    {
        return resource.TryGetProperty(parentName, out var parent)
            ? ReadDecimal(parent, propertyName)
            : null;
    }

    private static decimal? ReadDecimal(JsonElement resource, string propertyName)
    {
        if (!resource.TryGetProperty(propertyName, out var value))
        {
            return null;
        }

        return value.ValueKind switch
        {
            JsonValueKind.Number when value.TryGetDecimal(out var parsed) => parsed,
            JsonValueKind.String when decimal.TryParse(value.GetString(), NumberStyles.Any, CultureInfo.InvariantCulture, out var parsed) => parsed,
            _ => null
        };
    }

    private static string? ReadString(JsonElement resource, string propertyName)
    {
        if (!resource.TryGetProperty(propertyName, out var value))
        {
            return null;
        }

        return value.ValueKind switch
        {
            JsonValueKind.String => value.GetString(),
            JsonValueKind.Number => value.GetRawText(),
            JsonValueKind.True => "true",
            JsonValueKind.False => "false",
            _ => null
        };
    }

    private static string StableAnonymousId(string rawJson) => Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(rawJson)));
}
